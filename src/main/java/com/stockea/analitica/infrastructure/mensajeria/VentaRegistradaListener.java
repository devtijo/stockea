package com.stockea.analitica.infrastructure.mensajeria;

import com.stockea.analitica.application.VelocidadVentaService;
import com.stockea.ventas.api.VentaRegistrada;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

// Consumidor del Worker (perfil "worker", mismo JAR que la API)
// Si falla, Spring AMQP reintenta 3 veces con espera creciente; después el
//mensaje se rechaza y RabbitMQ lo manda a la cola de mensajes muerto

@Component
@Profile("worker")
class VentaRegistradaListener {

    private static final String PROCESADO = "procesado";
    private static final String DUPLICADO = "duplicado";
    private static final String ERROR = "error";

    private static final Logger log = LoggerFactory.getLogger(VentaRegistradaListener.class);

    private final VelocidadVentaService velocidad;
    private final MeterRegistry metricas;

    VentaRegistradaListener(VelocidadVentaService velocidad, MeterRegistry metricas) {
        this.velocidad = velocidad;
        this.metricas = metricas;
        for (var resultado : new String[]{PROCESADO, DUPLICADO, ERROR}) {
            metricas.counter("stockea.eventos.consumidos", "evento", "venta.registrada", "resultado", resultado);
        }
    }

    @RabbitListener(queues = "${stockea.mensajeria.cola-velocidad:analitica.velocidad-venta}")
    void alRegistrarseVenta(VentaRegistrada evento) {
        MDC.put("negocio_id", String.valueOf(evento.negocioId()));
        MDC.put("venta_id", String.valueOf(evento.ventaId()));
        try {
            boolean procesado = velocidad.registrar(evento);
            contar(procesado ? PROCESADO : DUPLICADO);
            log.info("Evento venta.registrada {}", procesado ? PROCESADO : DUPLICADO + ": se ignora");
        } catch (RuntimeException e) {
            contar(ERROR);
            log.warn("Error procesando venta.registrada: {}", e.getMessage());
            throw e; // lo maneja la política de reintentos y la DLQ
        } finally {
            MDC.remove("negocio_id");
            MDC.remove("venta_id");
        }
    }

    private void contar(String resultado) {
        metricas.counter("stockea.eventos.consumidos", "evento", "venta.registrada", "resultado", resultado).increment();
    }
}
