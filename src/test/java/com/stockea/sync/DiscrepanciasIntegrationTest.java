package com.stockea.sync;

import com.stockea.IntegracionBase;
import com.stockea.shared.seguridad.Rol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Bandeja de discrepancias del supervisor: listar las abiertas y resolverlas con una nota
class DiscrepanciasIntegrationTest extends IntegracionBase {

    private Sesion supervisor;
    private Sesion cajero;

    @BeforeEach
    void sembrar() throws Exception {
        supervisor = crearNegocioCon(Rol.SUPERVISOR);
        cajero = agregarUsuario(supervisor.negocioId(), UUID.randomUUID(), Rol.CAJERO);
        var producto = crearProducto(cajero, "Gaseosa", 2500);
        sembrarLote(cajero, producto, 1);
        venderPorSync(cajero, producto, 3, 2500); // hay 1: faltan 2
    }

    @Test
    @DisplayName("El supervisor ve la discrepancia abierta y la resuelve con una nota")
    void listarYResolver() throws Exception {
        var abiertas = jsonMapper.readTree(get("/api/discrepancias?estado=ABIERTA", supervisor.token()).body());
        assertThat(abiertas.size()).isEqualTo(1);
        assertThat(abiertas.get(0).get("motivo").asString()).isEqualTo("STOCK_INSUFICIENTE");
        var id = abiertas.get(0).get("id").asString();

        var r = post("/api/discrepancias/" + id + "/resolver", supervisor.token(),
                "{\"nota\":\"Conteo físico: había 3 en bodega sin registrar\"}");

        assertThat(r.statusCode()).isEqualTo(204);
        assertThat(jsonMapper.readTree(get("/api/discrepancias", supervisor.token()).body()).size()).isZero();
        var resueltas = jsonMapper.readTree(get("/api/discrepancias?estado=RESUELTA", supervisor.token()).body());
        assertThat(resueltas.get(0).get("notaResolucion").asString()).startsWith("Conteo físico");
        // Resolver otra vez: ya no está abierta
        assertThat(post("/api/discrepancias/" + id + "/resolver", supervisor.token(), "{\"nota\":\"otra\"}")
                .statusCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("Sin nota no se resuelve; un cajero no ve la bandeja")
    void reglas() throws Exception {
        var id = jsonMapper.readTree(get("/api/discrepancias", supervisor.token()).body()).get(0).get("id").asString();

        assertThat(post("/api/discrepancias/" + id + "/resolver", supervisor.token(), "{\"nota\":\" \"}")
                .statusCode()).isEqualTo(400);
        assertThat(get("/api/discrepancias", cajero.token()).statusCode()).isEqualTo(403);
    }
}
