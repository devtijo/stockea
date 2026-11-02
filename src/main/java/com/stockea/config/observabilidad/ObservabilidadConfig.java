package com.stockea.config.observabilidad;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration(proxyBeanMethods = false)
class ObservabilidadConfig {

    // Primero de todos: el request_id debe estar en TODOS los logs de la petición
    @Bean
    FilterRegistrationBean<CorrelacionFilter> correlacion() {
        var registro = new FilterRegistrationBean<>(new CorrelacionFilter());
        registro.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registro;
    }

    // Antes de Spring Security (orden -100) para registrar sus rechazos
    @Bean
    FilterRegistrationBean<EventosSeguridadFilter> eventosSeguridad(MeterRegistry metricas) {
        var registro = new FilterRegistrationBean<>(new EventosSeguridadFilter(metricas));
        registro.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registro;
    }
}
