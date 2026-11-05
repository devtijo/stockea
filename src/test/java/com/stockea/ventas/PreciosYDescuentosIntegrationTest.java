package com.stockea.ventas;

import com.stockea.IntegracionBase;
import com.stockea.shared.seguridad.Rol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// El servidor no recalcula lo cobrado compara con el precio y el descuento vigentes
// y, si no coinciden, acepta la venta y deja una discrepancia PRECIO_DIFERENTE (ADR-002)
class PreciosYDescuentosIntegrationTest extends IntegracionBase {

    private Sesion supervisor;
    private Sesion cajero;
    private UUID producto;
    private final UUID turno = UUID.randomUUID();

    @BeforeEach
    void sembrar() throws Exception {
        supervisor = crearNegocioCon(Rol.SUPERVISOR);
        cajero = agregarUsuario(supervisor.negocioId(), UUID.randomUUID(), Rol.CAJERO);
        var r = post("/api/inventario/productos", supervisor.token(),
                "{\"nombre\":\"Leche\",\"unidadVenta\":\"UNIDAD\",\"precio\":4000}");
        producto = UUID.fromString(jsonMapper.readTree(r.body()).get("id").asString());
    }

    @Test
    @DisplayName("Cobrar un precio distinto al vigente: venta aceptada con lo cobrado + discrepancia PRECIO_DIFERENTE")
    void precioDiferente() throws Exception {
        recibir("10", 20);

        var r = vender(1, venta(turno, cajero.usuarioId(), "EFECTIVO", producto, "2", "3500", "0"), Instant.now());

        assertThat(estado(r, 0)).isEqualTo("DISCREPANCIA");
        assertThat(valor("SELECT total FROM venta", BigDecimal.class)).isEqualByComparingTo("7000");
        assertThat(valor("SELECT motivo FROM discrepancia", String.class)).isEqualTo("PRECIO_DIFERENTE");
        assertThat(valor("SELECT precio_cobrado FROM discrepancia", BigDecimal.class)).isEqualByComparingTo("7000");
        assertThat(valor("SELECT precio_esperado FROM discrepancia", BigDecimal.class)).isEqualByComparingTo("8000");
    }

    @Test
    @DisplayName("Venta hecha sin internet ANTES de un cambio de precio: vale el precio de ese momento")
    void precioDelMomentoDeLaVenta() throws Exception {
        recibir("10", 20);
        // Entre crear el producto y cambiar el precio pasa al menos una petición HTTP completa
        var antesDelCambio = Instant.now();
        put("/api/inventario/productos/" + producto + "/precio", supervisor.token(), "{\"precio\": 5000}");

        var viejo = vender(1, venta(turno, cajero.usuarioId(), "EFECTIVO", producto, "1", "4000", "0"), antesDelCambio);
        var nuevo = vender(2, venta(turno, cajero.usuarioId(), "EFECTIVO", producto, "1", "4000", "0"), Instant.now());

        assertThat(estado(viejo, 0)).isEqualTo("ACEPTADA");
        assertThat(estado(nuevo, 0)).as("después del cambio, 4000 ya no es el precio").isEqualTo("DISCREPANCIA");
    }

    @Test
    @DisplayName("El descuento se aplica a las unidades del lote con descuento que FEFO entrega")
    void descuentoPorLoteFefo() throws Exception {
        var loteConDescuento = recibir("2", 3);   // vence primero: 25 % de descuento
        recibir("10", 20);
        crearDescuento(loteConDescuento, "25");

        // 3 unidades: 2 salen del lote con descuento (2 × 1000) y 1 del lote normal
        var r = vender(1, venta(turno, cajero.usuarioId(), "EFECTIVO", producto, "3", "4000", "2000"), Instant.now());

        assertThat(estado(r, 0)).isEqualTo("ACEPTADA");
        assertThat(valor("SELECT total FROM venta", BigDecimal.class)).isEqualByComparingTo("10000");
        assertThat(valor("SELECT monto_descuento FROM asignacion_lote WHERE lote_id = ?", BigDecimal.class,
                loteConDescuento)).isEqualByComparingTo("2000");
        assertThat(valor("SELECT descuento_sin_cobertura FROM item_venta", BigDecimal.class)).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Si el dispositivo descontó más de lo que respalda el lote real, queda constancia y discrepancia")
    void descuentoSinCobertura() throws Exception {
        var loteConDescuento = recibir("2", 3);
        recibir("10", 20);
        crearDescuento(loteConDescuento, "25");

        // El dispositivo creyó que las 3 unidades tenían descuento (3 × 1000)
        var r = vender(1, venta(turno, cajero.usuarioId(), "EFECTIVO", producto, "3", "4000", "3000"), Instant.now());

        assertThat(estado(r, 0)).isEqualTo("DISCREPANCIA");
        assertThat(valor("SELECT descuento_sin_cobertura FROM item_venta", BigDecimal.class)).isEqualByComparingTo("1000");
        assertThat(valor("SELECT motivo FROM discrepancia", String.class)).isEqualTo("PRECIO_DIFERENTE");
    }

    @Test
    @DisplayName("El catálogo del cajero muestra el descuento del lote que se venderá primero")
    void catalogoMuestraDescuento() throws Exception {
        var lote = recibir("2", 3);
        crearDescuento(lote, "25");

        var catalogo = jsonMapper.readTree(get("/api/catalogo", cajero.token()).body());
        assertThat(new BigDecimal(catalogo.get(0).get("descuento").get("porcentaje").toString()))
                .isEqualByComparingTo("25");
    }

    @Test
    @DisplayName("Un descuento por encima del tope del negocio se rechaza; un cajero no puede crearlo")
    void reglasDelDescuento() throws Exception {
        var lote = recibir("2", 3);
        var cuerpo = "{\"loteId\":\"" + lote + "\",\"porcentaje\":%s}";

        var fueraDeRango = post("/api/descuentos", supervisor.token(), cuerpo.formatted("80"));
        assertThat(fueraDeRango.statusCode()).isEqualTo(400);
        assertThat(fueraDeRango.body()).contains("PORCENTAJE_FUERA_DE_RANGO").contains("entre 0 y 30 %");
        assertThat(post("/api/descuentos", cajero.token(), cuerpo.formatted("10")).statusCode()).isEqualTo(403);
    }

    private java.net.http.HttpResponse<String> vender(long secuencia, Map<String, Object> payload, Instant fecha)
            throws Exception {
        return sincronizar(cajero, DISPOSITIVO, List.of(operacion("VENTA", secuencia, payload, fecha)));
    }

    private static final UUID DISPOSITIVO = UUID.randomUUID();

    private UUID recibir(String cantidad, int diasParaVencer) throws Exception {
        var r = post("/api/inventario/entradas", supervisor.token(), """
                {"productoId":"%s","cantidad":%s,"fechaVencimiento":"%s","costoUnitario":2500}
                """.formatted(producto, cantidad, LocalDate.now().plusDays(diasParaVencer)));
        return UUID.fromString(jsonMapper.readTree(r.body()).get("loteId").asString());
    }

    private void crearDescuento(UUID lote, String porcentaje) throws Exception {
        var r = post("/api/descuentos", supervisor.token(),
                "{\"loteId\":\"" + lote + "\",\"porcentaje\":" + porcentaje + "}");
        assertThat(r.statusCode()).isEqualTo(201);
    }

    private <T> T valor(String sql, Class<T> tipo, Object... args) {
        return enNegocio(supervisor.negocioId(), () -> jdbc.queryForObject(sql, tipo, args));
    }
}
