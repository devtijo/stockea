package com.stockea.caja;

import com.stockea.IntegracionBase;
import com.stockea.shared.seguridad.Rol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Turnos de caja como operaciones de la cola (ADR-025): abrir, vender, cerrar y cuadrar
class TurnosCajaIntegrationTest extends IntegracionBase {

    private Sesion supervisor;
    private Sesion cajero;
    private UUID dispositivo;
    private UUID producto;

    @BeforeEach
    void sembrar() {
        supervisor = crearNegocioCon(Rol.SUPERVISOR);
        cajero = agregarUsuario(supervisor.negocioId(), UUID.randomUUID(), Rol.CAJERO);
        dispositivo = UUID.randomUUID();
        producto = crearProducto(cajero, "Leche", 4000);
        sembrarLote(cajero, producto, 50);
    }

    @Test
    @DisplayName("Abrir, vender en efectivo y transferencia, cerrar: el servidor calcula el cuadre")
    void cuadreDeCaja() throws Exception {
        var turno = UUID.randomUUID();
        var r = sincronizar(cajero, dispositivo, List.of(
                operacion("ABRIR_TURNO", 1, Map.of("turnoId", turno, "cajeroId", cajero.usuarioId(), "montoInicial", 50000)),
                operacion("VENTA", 2, venta(turno, cajero.usuarioId(), "EFECTIVO", producto, "2", "4000", "0")),
                operacion("VENTA", 3, venta(turno, cajero.usuarioId(), "EFECTIVO", producto, "1", "4000", "0")),
                operacion("VENTA", 4, venta(turno, cajero.usuarioId(), "TRANSFERENCIA", producto, "3", "4000", "0")),
                operacion("CERRAR_TURNO", 5, Map.of("turnoId", turno, "montoContado", 61000))));

        assertThat(r.statusCode()).isEqualTo(200);
        for (int i = 0; i < 5; i++) assertThat(estado(r, i)).isEqualTo("ACEPTADA");

        var fila = valor("SELECT estado, efectivo_esperado, diferencia, ventas_cantidad FROM turno_caja WHERE id = '" + turno + "'");
        assertThat(fila.get("estado")).isEqualTo("CERRADO");
        assertThat((BigDecimal) fila.get("efectivo_esperado")).isEqualByComparingTo("62000"); // 50000 + 12000
        assertThat((BigDecimal) fila.get("diferencia")).isEqualByComparingTo("-1000");       // faltan 1000
        assertThat(fila.get("ventas_cantidad")).isEqualTo(3);

        // El supervisor ve el turno con el nombre del cajero y los totales por medio de pago
        var turnos = jsonMapper.readTree(get("/api/caja/turnos", supervisor.token()).body());
        assertThat(turnos.get(0).get("cajero").asString()).isEqualTo("Usuario CAJERO");
        assertThat(new BigDecimal(turnos.get(0).get("ventas").get("transferencia").toString()))
                .isEqualByComparingTo("12000");
        assertThat(get("/api/caja/turnos", cajero.token()).statusCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("Una venta sin turno se rechaza (RECHAZADA_SIN_TURNO) y no toca el stock")
    void ventaSinTurno() throws Exception {
        var payload = Map.of("metodoPago", "EFECTIVO", "montoRecibido", "4000", "totalCliente", "4000",
                "items", List.of(Map.of("productoId", producto.toString(), "cantidad", "1", "precioUnitario", "4000")));

        var r = sincronizar(cajero, dispositivo, List.of(operacion("VENTA", 1, payload)));

        assertThat(estado(r, 0)).isEqualTo("RECHAZADA_SIN_TURNO");
        assertThat(enNegocio(cajero.negocioId(), () -> jdbc.queryForObject("SELECT count(*) FROM venta", Integer.class)))
                .isZero();
    }

    @Test
    @DisplayName("Cerrar un turno que el servidor no conoce se rechaza")
    void cerrarTurnoDesconocido() throws Exception {
        var r = sincronizar(cajero, dispositivo, List.of(
                operacion("CERRAR_TURNO", 1, Map.of("turnoId", UUID.randomUUID(), "montoContado", 1000))));

        assertThat(estado(r, 0)).isEqualTo("RECHAZADA_SIN_TURNO");
    }

    @Test
    @DisplayName("La venta queda a nombre del cajero que la hizo, aunque sincronice otro usuario")
    void ventaANombreDelCajeroReal() throws Exception {
        var turno = UUID.randomUUID();
        // El supervisor sincroniza la cola del dispositivo donde vendió el cajero
        sincronizar(supervisor, dispositivo, List.of(
                operacion("ABRIR_TURNO", 1, Map.of("turnoId", turno, "cajeroId", cajero.usuarioId(), "montoInicial", 0)),
                operacion("VENTA", 2, venta(turno, cajero.usuarioId(), "EFECTIVO", producto, "1", "4000", "0")),
                // un cajeroId que no es del negocio: queda a nombre de quien sincroniza
                operacion("VENTA", 3, venta(turno, UUID.randomUUID(), "EFECTIVO", producto, "1", "4000", "0"))));

        var cajeros = enNegocio(cajero.negocioId(), () ->
                jdbc.queryForList("SELECT cajero_id FROM venta ORDER BY registrada_en", UUID.class));
        assertThat(cajeros).containsExactly(cajero.usuarioId(), supervisor.usuarioId());
    }

    private Map<String, Object> valor(String sql) {
        return enNegocio(cajero.negocioId(), () -> jdbc.queryForMap(sql));
    }
}
