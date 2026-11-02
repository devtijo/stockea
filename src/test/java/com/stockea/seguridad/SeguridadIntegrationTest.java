package com.stockea.seguridad;

import com.stockea.IntegracionBase;
import com.stockea.TokensDePrueba;
import com.stockea.shared.seguridad.Rol;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Zero Trust: todo se autentica, el negocio sale del token y nunca del cliente (OWASP A01/A07). */
class SeguridadIntegrationTest extends IntegracionBase {

    private static final String SYNC_VACIO = "{\"operaciones\":[]}";

    @Test
    @DisplayName("Sin token o con token inválido -> 401")
    void sinTokenNoEntra() throws Exception {
        assertThat(get("/api/catalogo", null).statusCode()).isEqualTo(401);
        assertThat(post("/api/sync/operaciones", null, SYNC_VACIO, "X-Dispositivo-Id", UUID.randomUUID().toString())
                .statusCode()).isEqualTo(401);
        assertThat(get("/api/catalogo", "token-falsificado").statusCode()).isEqualTo(401);
        assertThat(get("/api/yo", null).statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("Token válido pero sin negocio_id, o sin rol de STOCKEA -> 403")
    void tokenIncompletoSeRechaza() throws Exception {
        var sesion = crearNegocioCon(Rol.CAJERO);
        var sinNegocio = TokensDePrueba.token(null, sesion.usuarioId(), "CAJERO");
        var sinRol = TokensDePrueba.token(sesion.negocioId(), sesion.usuarioId(), "offline_access");

        assertThat(get("/api/catalogo", sinNegocio).statusCode()).isEqualTo(403);
        assertThat(get("/api/catalogo", sinRol).statusCode()).isEqualTo(403);
        assertThat(get("/api/catalogo", sesion.token()).statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("El catálogo de un negocio nunca muestra productos de otro")
    void catalogoAislado() throws Exception {
        var a = crearNegocioCon(Rol.CAJERO);
        var b = crearNegocioCon(Rol.CAJERO);
        enNegocio(b.negocioId(), () -> jdbc.update(
                "INSERT INTO producto (id, negocio_id, nombre, unidad_venta, precio_venta) VALUES (?,?,?,?,?)",
                UUID.randomUUID(), b.negocioId(), "Arroz de B", "UNIDAD", 3000));

        assertThat(get("/api/catalogo", a.token()).body()).isEqualTo("[]");
        assertThat(get("/api/catalogo", b.token()).body()).contains("Arroz de B");
    }

    @Test
    @DisplayName("Sincronizar exige el header X-Dispositivo-Id")
    void syncExigeDispositivo() throws Exception {
        var sesion = crearNegocioCon(Rol.CAJERO);
        assertThat(post("/api/sync/operaciones", sesion.token(), SYNC_VACIO).statusCode()).isEqualTo(400);
        assertThat(post("/api/sync/operaciones", sesion.token(), SYNC_VACIO,
                "X-Dispositivo-Id", UUID.randomUUID().toString()).statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("Zero Trust: un usuario inhabilitado no sincroniza aunque su token siga vigente")
    void usuarioInhabilitadoConTokenVigente() throws Exception {
        var admin = crearNegocioCon(Rol.ADMINISTRADOR);
        var cajero = agregarUsuario(admin.negocioId(), UUID.randomUUID(), Rol.CAJERO);
        assertThat(post("/api/usuarios/" + cajero.usuarioId() + "/inhabilitar", admin.token(), "").statusCode())
                .isEqualTo(204);

        assertThat(post("/api/sync/operaciones", cajero.token(), SYNC_VACIO,
                "X-Dispositivo-Id", UUID.randomUUID().toString()).statusCode()).isEqualTo(403);
        assertThat(get("/api/yo", cajero.token()).statusCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("Un token de un usuario de otro negocio no sirve aunque traiga mi negocio_id")
    void usuarioDeOtroNegocio() throws Exception {
        var a = crearNegocioCon(Rol.CAJERO);
        var b = crearNegocioCon(Rol.CAJERO);
        var mezclado = TokensDePrueba.token(a.negocioId(), b.usuarioId(), "CAJERO");
        assertThat(post("/api/sync/operaciones", mezclado, SYNC_VACIO,
                "X-Dispositivo-Id", UUID.randomUUID().toString()).statusCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("ADR-001: no existe POST /api/ventas; la venta solo entra por la cola de sincronización")
    void noHayEndpointDirectoDeVentas() throws Exception {
        var sesion = crearNegocioCon(Rol.ADMINISTRADOR);
        assertThat(post("/api/ventas", sesion.token(), "{}").statusCode()).isIn(404, 405);
    }

    @Test
    @DisplayName("Health es público, rutas desconocidas se niegan y hay cabeceras de seguridad")
    void bordes() throws Exception {
        assertThat(get("/actuator/health", null).statusCode()).isNotIn(401, 403);
        assertThat(get("/actuator/env", null).statusCode()).isIn(401, 403);
        var resp = get("/api/catalogo", null);
        assertThat(resp.headers().firstValue("X-Content-Type-Options")).hasValue("nosniff");
        assertThat(resp.headers().firstValue("Content-Security-Policy")).isPresent();
    }
}
