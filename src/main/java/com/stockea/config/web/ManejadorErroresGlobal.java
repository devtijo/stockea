package com.stockea.config.web;

import com.stockea.shared.domain.DomainException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

//Errores comunes como ProblemDetail (RFC 9457).
// Nunca devuelve trazas ni mensajes internos (OWASP A05).
// Las reglas de negocio de una venta NO llegan aquí: viajan dentro del 200 de /api/sync/operaciones

@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
class ManejadorErroresGlobal extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ManejadorErroresGlobal.class);

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail accesoDenegado(AccessDeniedException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "No tiene permisos para esta acción");
    }

    @ExceptionHandler({DomainException.class, IllegalArgumentException.class})
    ProblemDetail datosInvalidos(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail inesperado(Exception e) {
        log.error("Error no controlado", e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "Ocurrió un error interno. Intente de nuevo.");
    }
}
