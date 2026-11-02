package com.stockea.config.observabilidad;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

// OWASP A09 (registro y monitoreo): cada acceso rechazado queda en el log como
// evento de seguridad estructurado y suma a la métrica
// stockea_seguridad_eventos_total, sobre la que hay una alerta en Grafana.
// Va ANTES de la cadena de Spring Security para ver también sus 401/403

public class EventosSeguridadFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger("stockea.seguridad");

    private final MeterRegistry metricas;

    public EventosSeguridadFilter(MeterRegistry metricas) {
        this.metricas = metricas;
        // En 0 desde el arranque: así la alerta detecta también el PRIMER ataque
        for (var tipo : new String[]{"no_autenticado", "acceso_denegado", "cuerpo_demasiado_grande", "limite_de_peticiones"}) {
            metricas.counter("stockea.seguridad.eventos", "tipo", tipo);
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        chain.doFilter(request, response);
        String tipo = switch (response.getStatus()) {
            case 401 -> "no_autenticado";
            case 403 -> "acceso_denegado";
            case 413 -> "cuerpo_demasiado_grande";
            case 429 -> "limite_de_peticiones";
            default -> null;
        };
        if (tipo == null) {
            return;
        }
        metricas.counter("stockea.seguridad.eventos", "tipo", tipo).increment();
        log.atWarn()
                .addKeyValue("evento", "seguridad." + tipo)
                .addKeyValue("http_status", response.getStatus())
                .addKeyValue("http_metodo", request.getMethod())
                .addKeyValue("ruta", request.getRequestURI())
                .addKeyValue("ip_cliente", request.getRemoteAddr())
                .log("Acceso rechazado: {} {} -> {}", request.getMethod(), request.getRequestURI(), response.getStatus());
    }
}
