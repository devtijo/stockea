package com.stockea.inventario;

import com.stockea.IntegracionBase;
import com.stockea.shared.seguridad.Rol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Gestión de productos y recepción de mercancía por el supervisor, y el kardex que dejan
class InventarioIntegrationTest extends IntegracionBase {

    private Sesion supervisor;
    private Sesion cajero;

    @BeforeEach
    void sembrar() {
        supervisor = crearNegocioCon(Rol.SUPERVISOR);
        cajero = agregarUsuario(supervisor.negocioId(), UUID.randomUUID(), Rol.CAJERO);
    }

    @Test
    @DisplayName("Crear producto deja su primer precio en el historial")
    void crearProducto() throws Exception {
        var id = crearProductoApi("Arroz Diana 500 g", "4200");

        var productos = jsonMapper.readTree(get("/api/inventario/productos", supervisor.token()).body());
        assertThat(productos.size()).isEqualTo(1);
        assertThat(productos.get(0).get("nombre").asString()).isEqualTo("Arroz Diana 500 g");
        assertThat(contar("SELECT count(*) FROM historial_precio WHERE producto_id = ?", id)).isEqualTo(1);
    }

    @Test
    @DisplayName("Cambiar el precio actualiza el producto y agrega una fila al historial (no la reescribe)")
    void cambiarPrecio() throws Exception {
        var id = crearProductoApi("Leche", "4000");

        var r = put("/api/inventario/productos/" + id + "/precio", supervisor.token(), "{\"precio\": 4500}");

        assertThat(r.statusCode()).isEqualTo(204);
        assertThat(valor("SELECT precio_venta FROM producto WHERE id = ?", BigDecimal.class, id))
                .isEqualByComparingTo("4500");
        assertThat(contar("SELECT count(*) FROM historial_precio WHERE producto_id = ?", id)).isEqualTo(2);
    }

    @Test
    @DisplayName("Recibir mercancía crea el lote y un movimiento ENTRADA en el kardex")
    void recibirMercancia() throws Exception {
        var producto = crearProductoApi("Leche", "4000");

        var lote = recibir(producto, "24", LocalDate.now().plusDays(10));

        assertThat(valor("SELECT cantidad_disponible FROM lote WHERE id = ?", BigDecimal.class, lote))
                .isEqualByComparingTo("24");
        assertThat(valor("SELECT tipo FROM movimiento_inventario WHERE lote_id = ?", String.class, lote))
                .isEqualTo("ENTRADA");

        // La pantalla de inventario muestra el stock vendible y el lote
        var productos = jsonMapper.readTree(get("/api/inventario/productos", supervisor.token()).body());
        assertThat(new BigDecimal(productos.get(0).get("stock").toString())).isEqualByComparingTo("24");
        assertThat(productos.get(0).get("lotes").get(0).get("estado").asString()).isEqualTo("ACTIVO");
    }

    @Test
    @DisplayName("No se recibe mercancía vencida")
    void rechazaMercanciaVencida() throws Exception {
        var producto = crearProductoApi("Leche", "4000");
        var cuerpo = """
                {"productoId":"%s","cantidad":5,"fechaVencimiento":"%s","costoUnitario":2500}
                """.formatted(producto, LocalDate.now().minusDays(1));

        var r = post("/api/inventario/entradas", supervisor.token(), cuerpo);
        assertThat(r.statusCode()).isEqualTo(400);
        assertThat(r.body()).contains("MERCANCIA_VENCIDA");
    }

    @Test
    @DisplayName("Un cajero no puede crear productos ni cambiar precios (403)")
    void cajeroNoGestionaInventario() throws Exception {
        var cuerpo = "{\"nombre\":\"X\",\"unidadVenta\":\"UNIDAD\",\"precio\":1000}";
        assertThat(post("/api/inventario/productos", cajero.token(), cuerpo).statusCode()).isEqualTo(403);
        assertThat(get("/api/inventario/productos", cajero.token()).statusCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("Una venta que sale de dos lotes deja dos movimientos VENTA en el kardex")
    void ventaDejaKardex() throws Exception {
        var producto = crearProductoApi("Leche", "4000");
        var loteA = recibir(producto, "2", LocalDate.now().plusDays(3));
        var loteB = recibir(producto, "10", LocalDate.now().plusDays(20));

        var r = sincronizar(cajero, UUID.randomUUID(), List.of(operacion("VENTA", 1,
                venta(UUID.randomUUID(), cajero.usuarioId(), "EFECTIVO", producto, "5", "4000", "0"))));

        assertThat(estado(r, 0)).isEqualTo("ACEPTADA");
        assertThat(valor("SELECT SUM(cantidad) FROM movimiento_inventario WHERE tipo = 'VENTA' AND lote_id = ?",
                BigDecimal.class, loteA)).isEqualByComparingTo("2");
        assertThat(valor("SELECT SUM(cantidad) FROM movimiento_inventario WHERE tipo = 'VENTA' AND lote_id = ?",
                BigDecimal.class, loteB)).isEqualByComparingTo("3");
        assertThat(contar("SELECT count(*) FROM movimiento_inventario WHERE tipo = 'VENTA' AND creado_por = ?",
                cajero.usuarioId())).isEqualTo(2);
    }

    private UUID crearProductoApi(String nombre, String precio) throws Exception {
        var r = post("/api/inventario/productos", supervisor.token(),
                "{\"nombre\":\"%s\",\"categoria\":\"Abarrotes\",\"unidadVenta\":\"UNIDAD\",\"precio\":%s}"
                        .formatted(nombre, precio));
        assertThat(r.statusCode()).isEqualTo(201);
        return UUID.fromString(jsonMapper.readTree(r.body()).get("id").asString());
    }

    private UUID recibir(UUID producto, String cantidad, LocalDate vence) throws Exception {
        var r = post("/api/inventario/entradas", supervisor.token(), """
                {"productoId":"%s","cantidad":%s,"fechaVencimiento":"%s","costoUnitario":2500}
                """.formatted(producto, cantidad, vence));
        assertThat(r.statusCode()).isEqualTo(201);
        return UUID.fromString(jsonMapper.readTree(r.body()).get("loteId").asString());
    }

    private int contar(String sql, Object... args) {
        return valor(sql, Integer.class, args);
    }

    private <T> T valor(String sql, Class<T> tipo, Object... args) {
        return enNegocio(supervisor.negocioId(), () -> jdbc.queryForObject(sql, tipo, args));
    }
}
