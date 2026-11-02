package com.stockea.analitica;

import com.stockea.IntegracionBase;
import com.stockea.shared.seguridad.Rol;
import com.stockea.ventas.api.VentaRegistrada;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Message broker de punta a punta: venta por HTTP → evento en
 * el outbox → RabbitMQ tras el COMMIT → Worker → velocidad de venta
 * Este contexto activa el perfil "worker" para tener también al consumidor.
 */
@ActiveProfiles("worker")
@TestPropertySource(properties = "spring.flyway.enabled=true")
class EventosVentaIntegrationTest extends IntegracionBase {

    @Autowired RabbitTemplate rabbit;

    private BigDecimal unidadesHoy(Sesion s, UUID producto) {
        return enNegocio(s.negocioId(), () -> jdbc.queryForObject(
                "SELECT COALESCE(SUM(unidades), 0) FROM venta_diaria_producto WHERE producto_id = ?",
                BigDecimal.class, producto));
    }

    @Test
    @DisplayName("Una venta llega al Worker por RabbitMQ y suma a la velocidad de venta")
    void ventaLlegaAlWorker() throws Exception {
        var cajero = crearNegocioCon(Rol.CAJERO);
        var leche = crearProducto(cajero, "Leche", 4000);
        sembrarLote(cajero, leche, 50);

        assertThat(venderPorSync(cajero, leche, 3, 4000).statusCode()).isEqualTo(200);
        assertThat(venderPorSync(cajero, leche, 2, 4000).statusCode()).isEqualTo(200);

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(unidadesHoy(cajero, leche)).isEqualByComparingTo("5"));
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(jdbc.queryForObject("SELECT count(*) FROM event_publication WHERE completion_date IS NULL",
                        Integer.class)).as("el outbox queda vacío").isZero());
    }

    @Test
    @DisplayName("Idempotencia del consumidor: el mismo evento entregado dos veces cuenta una sola vez")
    void eventoDuplicadoSeIgnora() throws Exception {
        var cajero = crearNegocioCon(Rol.CAJERO);
        var pan = crearProducto(cajero, "Pan", 6500);
        var evento = new VentaRegistrada(cajero.negocioId(), UUID.randomUUID(), UUID.randomUUID(), "ACEPTADA",
                LocalDate.now(), new BigDecimal("13000"), "EFECTIVO", Instant.now(),
                List.of(new VentaRegistrada.Item(pan, new BigDecimal("2"))));

        rabbit.convertAndSend("stockea.ventas", "venta.registrada", evento);
        rabbit.convertAndSend("stockea.ventas", "venta.registrada", evento);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(enNegocio(cajero.negocioId(), () -> jdbc.queryForObject(
                        "SELECT count(*) FROM evento_consumido", Integer.class))).isEqualTo(1));
        Thread.sleep(1000); // margen para que el segundo mensaje también se procese
        assertThat(unidadesHoy(cajero, pan)).isEqualByComparingTo("2");
        assertThat(metricas()).contains("resultado=\"duplicado\"");
    }

    @Test
    @DisplayName("Un mensaje que no se puede leer termina en la cola de mensajes muertos (DLQ)")
    void mensajeEnvenenadoVaALaDlq() {
        var basura = MessageBuilder.withBody("esto no es json".getBytes())
                .setContentType(MessageProperties.CONTENT_TYPE_JSON).build();
        rabbit.send("stockea.ventas", "venta.registrada", basura);

        var muerto = rabbit.receive("analitica.velocidad-venta.dlq", 20_000);
        assertThat(muerto).as("el mensaje debe estar en la DLQ, no perdido ni en bucle").isNotNull();
        assertThat(new String(muerto.getBody())).isEqualTo("esto no es json");
    }

    @Test
    @DisplayName("Velocidad de venta y sugerencia de reposición (ADR-006): solo SUPERVISOR o más")
    void consultaDeVelocidad() throws Exception {
        var supervisor = crearNegocioCon(Rol.SUPERVISOR);
        var cajero = agregarUsuario(supervisor.negocioId(), UUID.randomUUID(), Rol.CAJERO);
        var huevos = crearProducto(supervisor, "Huevos", 9800);
        sembrarLote(supervisor, huevos, 4);
        enNegocio(supervisor.negocioId(), () -> jdbc.update(
                "INSERT INTO venta_diaria_producto (negocio_id, producto_id, fecha, unidades, ventas) "
                        + "VALUES (?, ?, CURRENT_DATE, 14, 5)", supervisor.negocioId(), huevos));

        var resp = get("/api/analitica/velocidad?dias=7", supervisor.token());

        assertThat(resp.statusCode()).isEqualTo(200);
        var fila = jsonMapper.readTree(resp.body()).get(0);
        assertThat(fila.get("velocidadDiaria").decimalValue()).isEqualByComparingTo("2.00");   // 14 / 7
        assertThat(fila.get("diasCobertura").decimalValue()).isEqualByComparingTo("2.0");      // 4 / 2
        assertThat(fila.get("sugerencia").asString()).isEqualTo("REPONER");
        assertThat(get("/api/analitica/velocidad", cajero.token()).statusCode()).isEqualTo(403);
    }
}
