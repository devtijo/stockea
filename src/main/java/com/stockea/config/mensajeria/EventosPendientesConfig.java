package com.stockea.config.mensajeria;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;

//Reenvia cada minuto los eventos pendientes + métricas del outbox
//Solo corre en la API

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@Profile("!worker")
class EventosPendientesConfig {

    private static final Logger log = LoggerFactory.getLogger(EventosPendientesConfig.class);

    private final IncompleteEventPublications pendientes;

    EventosPendientesConfig(IncompleteEventPublications pendientes, JdbcTemplate jdbc, MeterRegistry metricas) {
        this.pendientes = pendientes;
        Gauge.builder("stockea.eventos.pendientes", jdbc,
                        j -> j.queryForObject("SELECT count(*) FROM event_publication WHERE completion_date IS NULL",
                                Long.class))
                .description("Eventos de dominio guardados que aún no llegan a RabbitMQ")
                .register(metricas);
    }

    @Scheduled(initialDelay = 60_000, fixedDelay = 60_000)
    void reenviar() {
        try {
            pendientes.resubmitIncompletePublicationsOlderThan(Duration.ofMinutes(1));
        } catch (RuntimeException e) {
            log.warn("No se pudieron reenviar los eventos pendientes: {}", e.getMessage());
        }
    }
}