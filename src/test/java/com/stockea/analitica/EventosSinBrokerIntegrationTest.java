package com.stockea.analitica;

import com.stockea.IntegracionBase;
import com.stockea.shared.seguridad.Rol;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Resiliencia (Reactive Manifesto): con RabbitMQ caído la venta se
 * registra igual. El evento queda pendiente en el outbox y se reenvía después.*/

// "addresses" tiene prioridad sobre host/port en Spring Boot: apunta a un puerto donde nadie escucha
@TestPropertySource(properties = "spring.rabbitmq.addresses=localhost:1")
class EventoSinBrokerIntegrationTest extends IntegracionBase {

    @Test
    @DisplayName("Sin RabbitMQ la venta hace COMMIT y el evento queda pendiente en el outbox")
    void ventaNoDependeDelBroker() throws Exception {
        var cajero = crearNegocioCon(Rol.CAJERO);
        var arroz = crearProducto(cajero, "Arroz", 2600);
        sembrarLote(cajero, arroz, 10);

        assertThat(venderPorSync(cajero, arroz, 1, 2600).statusCode()).isEqualTo(200);

        assertThat(enNegocio(cajero.negocioId(), () ->
                jdbc.queryForObject("SELECT count(*) FROM venta", Integer.class))).isEqualTo(1);
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(metricas()).containsPattern("stockea_eventos_pendientes\\{[^}]*\\} 1\\.0"));
    }
}
