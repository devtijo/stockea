package com.stockea.sync;

import com.stockea.TestConstants;
import com.stockea.shared.dev.TenantDev;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

// Prueba de integración de punta a punta: HTTP real -> SyncService -> FEFO -> PostgreSQL real.
// Cubre SPK-002/003/006
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(RegistrarVentaIntegrationTest.PostgresConfig.class)
class RegistrarVentaIntegrationTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class PostgresConfig {
        @Bean
        @ServiceConnection
        PostgreSQLContainer postgres() {
            return new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));
        }
    }

    private static final UUID NEGOCIO = TenantDev.NEGOCIO_ID();
    private static final LocalDate HOY = LocalDate.now(ZoneId.of("America/Bogota"));

    @Value("${local.server.port}")
    int puerto;
    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper json;

    private final HttpClient http = HttpClient.newHttpClient();
    private UUID producto;
    private UUID loteA;   // vence primero
    private UUID loteB;

    @BeforeEach
    void limpiarYSembrar() {
        jdbc.execute("TRUNCATE discrepancia, asignacion_lote, item_venta, venta, movimiento_inventario, "
                + "operacion_sync, historial_precio, lote, producto CASCADE");
        producto = UUID.randomUUID();
        jdbc.update("INSERT INTO producto (id, negocio_id, nombre, unidad_venta, precio_venta) VALUES (?,?,?,?,?)",
                producto, NEGOCIO, TestConstants.PRODUCTO_LECHE, TestConstants.UNIDAD_VENTA, new BigDecimal(TestConstants.PRECIO_LECHE));
        loteA = UUID.randomUUID();
        loteB = UUID.randomUUID();
    }

    // FEFO

    @Test
    @DisplayName("ESC-CON-0014: vender 3 con lote A=7 (vence antes) y B=3 -> A=4, B intacto")
    void descuentaDelLoteQueVenceAntes() throws Exception {
        sembrarLote(loteA, "7", HOY.plusDays(5));
        sembrarLote(loteB, "3", HOY.plusDays(20));

        var resp = enviarVenta(UUID.randomUUID(), 1, producto, "3");

        assertThat(resp.statusCode()).isEqualTo(200);
        assertThat(disponible(loteA)).isEqualByComparingTo("4");
        assertThat(disponible(loteB)).isEqualByComparingTo("3");
        assertThat(contar("venta")).isEqualTo(1);
        assertThat(contar("item_venta")).isEqualTo(1);
        assertThat(contar("asignacion_lote")).isEqualTo(1);
        assertThat(contar("operacion_sync")).isEqualTo(1);
    }

    @Test
    @DisplayName("ADR-003: vender 9 fracciona en UN item_venta con DOS asignaciones; A se agota")
    void fraccionaEntreLotes() throws Exception {
        sembrarLote(loteA, "7", HOY.plusDays(5));
        sembrarLote(loteB, "3", HOY.plusDays(20));

        enviarVenta(UUID.randomUUID(), 1, producto, "9");

        assertThat(disponible(loteA)).isEqualByComparingTo("0");
        assertThat(jdbc.queryForObject("SELECT estado FROM lote WHERE id = ?", String.class, loteA)).isEqualTo(TestConstants.ESTADO_AGOTADO);
        assertThat(disponible(loteB)).isEqualByComparingTo("1");
        assertThat(contar("item_venta")).isEqualTo(1);
        assertThat(contar("asignacion_lote")).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT cantidad_sin_cobertura FROM item_venta", BigDecimal.class))
                .isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("ADR-023: un lote vencido a la fecha de venta no se usa")
    void ignoraLotesVencidos() throws Exception {
        sembrarLote(loteA, "10", HOY.minusDays(1));   // ya vencido
        sembrarLote(loteB, "10", HOY.plusDays(10));

        enviarVenta(UUID.randomUUID(), 1, producto, "4");

        assertThat(disponible(loteA)).isEqualByComparingTo("10");
        assertThat(disponible(loteB)).isEqualByComparingTo("6");
    }

    // SPK-002: discrepancia

    @Test
    @DisplayName("ESC-DIS-0008: stock insuficiente -> venta aceptada, stock nunca negativo, discrepancia ABIERTA")
    void stockInsuficienteGeneraDiscrepancia() throws Exception {
        sembrarLote(loteA, "5", HOY.plusDays(5));

        var resp = enviarVenta(UUID.randomUUID(), 1, producto, "8");

        assertThat(resp.statusCode()).isEqualTo(200);
        assertThat(contar("venta")).as("la venta se acepta siempre (ADR-002)").isEqualTo(1);
        assertThat(disponible(loteA)).as("el lote queda en 0, nunca negativo").isEqualByComparingTo("0");
        assertThat(jdbc.queryForObject("SELECT cantidad_sin_cobertura FROM item_venta", BigDecimal.class))
                .isEqualByComparingTo("3");
        assertThat(jdbc.queryForObject("SELECT estado FROM venta", String.class)).isEqualTo(TestConstants.ESTADO_DISCREPANCIA);
        assertThat(jdbc.queryForObject("SELECT estado FROM operacion_sync", String.class))
                .as("el estado de la operación refleja la discrepancia").isEqualTo(TestConstants.ESTADO_DISCREPANCIA);
        assertThat(contar("discrepancia")).as("debe existir 1 discrepancia ABIERTA").isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT cantidad_faltante FROM discrepancia", BigDecimal.class))
                .isEqualByComparingTo("3");
    }

    @Test
    @DisplayName("SPK-002: dos dispositivos venden del mismo lote (A=8, luego B=5, lote de 10)")
    void dosDispositivosMismoLote() throws Exception {
        sembrarLote(loteA, "10", HOY.plusDays(5));

        enviarVenta(UUID.randomUUID(), 1, producto, "8");
        enviarVenta(UUID.randomUUID(), 2, producto, "5");

        assertThat(contar("venta")).isEqualTo(2);
        assertThat(disponible(loteA)).isEqualByComparingTo("0");
        assertThat(jdbc.queryForObject("SELECT SUM(cantidad_sin_cobertura) FROM item_venta", BigDecimal.class))
                .isEqualByComparingTo("3");
    }

    // SPK-006: idempotencia

    @Test
    @DisplayName("ADR-002: reenviar el mismo operacionId devuelve la misma venta y descuenta una sola vez")
    void reenvioEsIdempotente() throws Exception {
        sembrarLote(loteA, "10", HOY.plusDays(5));
        var operacion = UUID.randomUUID();

        var r1 = enviarVenta(operacion, 1, producto, "2");
        var r2 = enviarVenta(operacion, 1, producto, "2");

        assertThat(r1.statusCode()).isEqualTo(200);
        assertThat(r2.statusCode()).isEqualTo(200);
        assertThat(resultadoId(r2)).isEqualTo(resultadoId(r1));
        assertThat(contar("venta")).isEqualTo(1);
        assertThat(contar("operacion_sync")).isEqualTo(1);
        assertThat(disponible(loteA)).isEqualByComparingTo("8");
    }

    @Test
    @DisplayName("SPK-006: 8 reintentos SIMULTÁNEOS del mismo operacionId producen una sola venta")
    void reintentosConcurrentesNoDuplican() throws Exception {
        sembrarLote(loteA, "100", HOY.plusDays(5));
        var operacion = UUID.randomUUID();

        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Future<HttpResponse<String>>> futuros = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            Callable<HttpResponse<String>> tarea = () -> enviarVenta(operacion, 1, producto, "1");
            futuros.add(pool.submit(tarea));
        }
        for (var f : futuros) {
            assertThat(f.get().statusCode()).isEqualTo(200);
        }
        pool.shutdown();

        assertThat(contar("venta")).isEqualTo(1);
        assertThat(contar("operacion_sync")).isEqualTo(1);
        assertThat(disponible(loteA)).isEqualByComparingTo("99");
    }

    // ESC-CON-0001: atomicidad

    @Test
    @DisplayName("ESC-CON-0001: si algo falla a la mitad, el descuento de stock también se revierte")
    void falloIntermedioRevierteTodo() throws Exception {
        sembrarLote(loteA, "10", HOY.plusDays(5));
        var productoInexistente = UUID.randomUUID();   // viola la FK al guardar el item

        var resp = enviarVentaConDosItems(UUID.randomUUID(), 1, producto, "2", productoInexistente, "1");

        assertThat(resp.statusCode()).isGreaterThanOrEqualTo(500);
        assertThat(disponible(loteA)).as("el descuento del primer item debe revertirse").isEqualByComparingTo("10");
        assertThat(contar("venta")).isZero();
        assertThat(contar("operacion_sync")).isZero();
    }

    // utilidades

    private void sembrarLote(UUID id, String cantidad, LocalDate vence) {
        jdbc.update("INSERT INTO lote (id, negocio_id, producto_id, fecha_vencimiento, cantidad_inicial, "
                        + "cantidad_disponible, costo_unitario) VALUES (?,?,?,?,?,?,?)",
                id, NEGOCIO, producto, vence, new BigDecimal(cantidad), new BigDecimal(cantidad), new BigDecimal(TestConstants.COSTO_LECHE));
    }

    private BigDecimal disponible(UUID lote) {
        return jdbc.queryForObject("SELECT cantidad_disponible FROM lote WHERE id = ?", BigDecimal.class, lote);
    }

    private int contar(String tabla) {
        return jdbc.queryForObject("SELECT count(*) FROM " + tabla, Integer.class);
    }

    private UUID resultadoId(HttpResponse<String> r) throws Exception {
        var nodo = json.readTree(r.body()).get("resultados").get(0).get("resultadoId");
        return UUID.fromString(nodo.asString());
    }

    private HttpResponse<String> enviarVenta(UUID operacionId, long secuencia, UUID productoId, String cantidad)
            throws Exception {
        return post(operacionId, secuencia, List.of(item(productoId, cantidad)));
    }

    private HttpResponse<String> enviarVentaConDosItems(UUID operacionId, long secuencia,
                                                        UUID p1, String c1, UUID p2, String c2) throws Exception {
        return post(operacionId, secuencia, List.of(item(p1, c1), item(p2, c2)));
    }

    private Map<String, Object> item(UUID productoId, String cantidad) {
        return Map.of("productoId", productoId.toString(), "cantidad", cantidad, "precioUnitario", TestConstants.PRECIO_LECHE);
    }

    private HttpResponse<String> post(UUID operacionId, long secuencia, List<Map<String, Object>> items)
            throws Exception {
        var total = items.stream()
                .map(i -> new BigDecimal((String) i.get("cantidad")).multiply(new BigDecimal(TestConstants.PRECIO_LECHE)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        var payload = Map.of(
                "metodoPago", TestConstants.METODO_PAGO_EFECTIVO,
                "montoRecibido", total.add(new BigDecimal("10000")).toPlainString(),
                "totalCliente", total.toPlainString(),
                "items", items
        );

        var operacion = Map.of(
                "operacionId", operacionId.toString(),
                "secuenciaDispositivo", secuencia,
                "tipo", TestConstants.TIPO_VENTA,
                "fechaCliente", Instant.now().minusSeconds(30).toString(),
                "payloadJson", json.writeValueAsString(payload));

        var cuerpo = json.writeValueAsString(Map.of("operaciones", List.of(operacion)));

        var req = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + TestConstants.ENDPOINT_SYNC))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo))
                .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }
}