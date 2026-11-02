package com.stockea.identidad;

import com.stockea.IntegracionBase;
import com.stockea.TokensDePrueba;
import com.stockea.shared.seguridad.Rol;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Alta de un negocio nuevo con su administrador (endpoint público, ESC-SEG / Ley 1581). */
class RegistroNegocioIntegrationTest extends IntegracionBase {

    @Autowired JsonMapper json;

    private String solicitud(String documento, boolean acepta, String clave, String pin) {
        return """
                {"negocio":{"nombre":"Tienda Doña Rosa","tipo":"TIENDA","ciudad":"Rionegro","direccion":"Cra 50 # 10-20"},
                 "administrador":{"documento":{"tipo":"CC","numero":"%s"},"nombre":"Rosa Pérez",
                                  "correo":"rosa@example.com","clave":"%s","pin":"%s"},
                 "aceptaPolitica":%s,"versionPolitica":"2026-10"}""".formatted(documento, clave, pin, acepta);
    }

    @Test
    @DisplayName("Registrar negocio crea negocio, administrador, consentimiento y cuenta en el IdP")
    void registraNegocioCompleto() throws Exception {
        var resp = post("/api/publico/negocios", null, solicitud("1020304050", true, "Clave2026", "4821"));

        assertThat(resp.statusCode()).isEqualTo(201);
        var cuerpo = json.readTree(resp.body());
        var negocio = UUID.fromString(cuerpo.get("negocioId").asString());
        var usuario = UUID.fromString(cuerpo.get("usuarioId").asString());
        assertThat(cuerpo.get("usuario").asString()).isEqualTo("cc-1020304050");

        enNegocio(negocio, () -> {
            assertThat(jdbc.queryForObject("SELECT nombre FROM negocio", String.class)).isEqualTo("Tienda Doña Rosa");
            assertThat(jdbc.queryForObject("SELECT rol FROM usuario WHERE id = ?", String.class, usuario))
                    .isEqualTo("ADMINISTRADOR");
            assertThat(jdbc.queryForObject("SELECT version_politica FROM consentimiento", String.class))
                    .isEqualTo("2026-10");
            assertThat(jdbc.queryForObject("SELECT pin_hash FROM usuario", String.class))
                    .as("el PIN nunca se guarda en claro").isNotEqualTo("4821").hasSizeGreaterThan(20);
            return null;
        });
        var cuenta = proveedor.cuentas.get(usuario);
        assertThat(cuenta.rol()).isEqualTo(Rol.ADMINISTRADOR);
        assertThat(cuenta.negocioId()).as("el IdP pondrá negocio_id en el token").isEqualTo(negocio);
        assertThat(cuenta.claveTemporal()).isFalse();

        // Con su token ya puede consultar su perfil
        var yo = get("/api/yo", TokensDePrueba.token(negocio, usuario, "ADMINISTRADOR"));
        assertThat(yo.statusCode()).isEqualTo(200);
        assertThat(json.readTree(yo.body()).get("negocio").asString()).isEqualTo("Tienda Doña Rosa");
    }

    @Test
    @DisplayName("El mismo documento no puede registrar dos negocios -> 409")
    void documentoDuplicado() throws Exception {
        assertThat(post("/api/publico/negocios", null, solicitud("1020304050", true, "Clave2026", "4821"))
                .statusCode()).isEqualTo(201);

        var otra = post("/api/publico/negocios", null, solicitud("1020304050", true, "Clave2026", "4821"));

        assertThat(otra.statusCode()).isEqualTo(409);
        assertThat(json.readTree(otra.body()).get("codigo").asString()).isEqualTo("DOCUMENTO_YA_REGISTRADO");
        assertThat(proveedor.cuentas).hasSize(1);
    }

    @Test
    @DisplayName("Si falla la base después de crear la cuenta, la cuenta del IdP se elimina (compensación)")
    void compensaSiFallaLaBase() throws Exception {
        assertThat(post("/api/publico/negocios", null, solicitud("1020304050", true, "Clave2026", "4821"))
                .statusCode()).isEqualTo(201);
        proveedor.reiniciar();   // el IdP "olvida" la cuenta: la base es la que rechazará el duplicado

        var resp = post("/api/publico/negocios", null, solicitud("1020304050", true, "Clave2026", "4821"));

        assertThat(resp.statusCode()).isGreaterThanOrEqualTo(400);
        assertThat(proveedor.eliminadas).as("no quedan cuentas huérfanas en el IdP").hasSize(1);
        assertThat(proveedor.cuentas).isEmpty();
    }

    @Test
    @DisplayName("Sin aceptar la política de datos, PIN inválido o clave débil -> 400 y nada se crea")
    void validaciones() throws Exception {
        assertThat(post("/api/publico/negocios", null, solicitud("1020304050", false, "Clave2026", "4821"))
                .statusCode()).isEqualTo(400);
        assertThat(post("/api/publico/negocios", null, solicitud("1020304050", true, "Clave2026", "12"))
                .statusCode()).isEqualTo(400);
        assertThat(post("/api/publico/negocios", null, solicitud("12", true, "Clave2026", "4821"))
                .statusCode()).isEqualTo(400);
        var debil = post("/api/publico/negocios", null, solicitud("1020304050", true, "solotexto", "4821"));
        assertThat(debil.statusCode()).isEqualTo(400);
        assertThat(json.readTree(debil.body()).get("codigo").asString()).isEqualTo("CLAVE_NO_CUMPLE_POLITICA");
        assertThat(proveedor.cuentas).isEmpty();
    }
}
