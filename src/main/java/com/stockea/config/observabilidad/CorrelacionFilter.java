package com.stockea.config.observabilidad;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

// Identificador de petición de punta a punta: Nginx genera X-Request-Id, el backend lo pone en cada línea de log (MDC request_id)
// y lo devuelve en la respuesta. Con ese id se sigue una petición en el log del gateway y del backend

public class CorrelacionFilter extends OncePerRequestFilter {

    static final String CABECERA = "X-Request-Id";
    private static final Pattern VALIDO = Pattern.compile("[A-Za-z0-9-]{8,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String id = request.getHeader(CABECERA);
        if (id == null || !VALIDO.matcher(id).matches()) {
            id = UUID.randomUUID().toString(); // no se confía en valores raros (inyección en logs)
        }
        MDC.put("request_id", id);
        response.setHeader(CABECERA, id);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove("request_id");
        }
    }
}
