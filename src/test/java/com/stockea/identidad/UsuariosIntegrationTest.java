package com.stockea.identidad;

import com.stockea.IntegracionBase;
import com.stockea.shared.seguridad.Rol;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Gestión de usuarios del negocio: solo el ADMINISTRADOR y solo dentro de su negocio (ADR-005, ADR-009). */
class UsuariosIntegrationTest extends IntegracionBase {

    @Autowired JsonMapper json;

    private static String nuevo(String documento, String rol) {
        return """
                {"documento":{"tipo":"CC","numero":"%s"},"nombre":"Carlos Cajero","correo":null,
                 "rol":"%s","claveTemporal":"Temporal2026","pin":"1234"}""".formatted(documento, rol);
    }

    @Test
    @DisplayName("El administrador crea un cajero con clave temporal y aparece en la lista")
    void adminCreaCajero() throws Exception {
        var admin = crearNegocioCon(Rol.ADMINISTRADOR);

        var resp = post("/api/usuarios", admin.token(), nuevo("9876543210", "CAJERO"));

        assertThat(resp.statusCode()).isEqualTo(201);
        var id = UUID.fromString(json.readTree(resp.body()).get("id").asString());
        var cuenta = proveedor.cuentas.get(id);
        assertThat(cuenta.rol()).isEqualTo(Rol.CAJERO);
        assertThat(cuenta.negocioId()).isEqualTo(admin.negocioId());
        assertThat(cuenta.claveTemporal()).as("debe cambiarla en el primer ingreso").isTrue();

        var lista = json.readTree(get("/api/usuarios", admin.token()).body());
        assertThat(lista.size()).isEqualTo(2);
    }

    @Test
    @DisplayName("Cajero y supervisor no pueden crear ni listar usuarios -> 403")
    void soloAdministrador() throws Exception {
        var cajero = crearNegocioCon(Rol.CAJERO);
        var supervisor = agregarUsuario(cajero.negocioId(), UUID.randomUUID(), Rol.SUPERVISOR);

        assertThat(post("/api/usuarios", cajero.token(), nuevo("9876543210", "CAJERO")).statusCode()).isEqualTo(403);
        assertThat(post("/api/usuarios", supervisor.token(), nuevo("9876543210", "CAJERO")).statusCode())
                .isEqualTo(403);
        assertThat(get("/api/usuarios", supervisor.token()).statusCode()).isEqualTo(403);
        assertThat(proveedor.cuentas).isEmpty();
    }

    @Test
    @DisplayName("Un administrador no ve ni puede inhabilitar usuarios de otro negocio")
    void aislamientoEntreNegocios() throws Exception {
        var adminA = crearNegocioCon(Rol.ADMINISTRADOR);
        var cajeroA = agregarUsuario(adminA.negocioId(), UUID.randomUUID(), Rol.CAJERO);
        var adminB = crearNegocioCon(Rol.ADMINISTRADOR);

        var listaB = json.readTree(get("/api/usuarios", adminB.token()).body());
        assertThat(listaB.size()).as("solo se ve a sí mismo").isEqualTo(1);

        var resp = post("/api/usuarios/" + cajeroA.usuarioId() + "/inhabilitar", adminB.token(), "");
        assertThat(resp.statusCode()).isEqualTo(404);
        assertThat(proveedor.deshabilitadas).isEmpty();
    }

    @Test
    @DisplayName("Inhabilitar un cajero lo bloquea en el IdP; el admin no puede inhabilitarse a sí mismo")
    void inhabilitar() throws Exception {
        var admin = crearNegocioCon(Rol.ADMINISTRADOR);
        var cajero = agregarUsuario(admin.negocioId(), UUID.randomUUID(), Rol.CAJERO);

        assertThat(post("/api/usuarios/" + cajero.usuarioId() + "/inhabilitar", admin.token(), "").statusCode())
                .isEqualTo(204);
        assertThat(proveedor.deshabilitadas).containsExactly(cajero.usuarioId());
        assertThat(enNegocio(admin.negocioId(), () ->
                jdbc.queryForObject("SELECT activo FROM usuario WHERE id = ?", Boolean.class, cajero.usuarioId())))
                .isFalse();

        assertThat(post("/api/usuarios/" + admin.usuarioId() + "/inhabilitar", admin.token(), "").statusCode())
                .isEqualTo(400);
    }
}
