package com.stockea.config.web;

import com.stockea.shared.domain.CodigoError;
import com.stockea.shared.domain.DomainException;
import com.stockea.shared.domain.ReglaNegocioException;
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

//Errores comunes como ProblemDetail
// Las reglas de negocio de una venta NO llegan aquí: viajan dentro del 200 de /api/sync/operaciones

@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
class ManejadorErroresGlobal extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ManejadorErroresGlobal.class);

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail accesoDenegado(AccessDeniedException e) {
        return problema(HttpStatus.FORBIDDEN, ErrorGeneral.ACCESO_DENEGADO, ErrorGeneral.ACCESO_DENEGADO.mensaje());
    }

    // Reglas de negocio de inventario, descuentos, caja y sync: código + mensaje del enum del módulo
    @ExceptionHandler(ReglaNegocioException.class)
    ProblemDetail reglaIncumplida(ReglaNegocioException e) {
        var estado = switch (e.error().tipo()) {
            case NO_ENCONTRADO -> HttpStatus.NOT_FOUND;
            case CONFLICTO -> HttpStatus.CONFLICT;
            case INVALIDO -> HttpStatus.BAD_REQUEST;
        };
        return problema(estado, e.error(), e.getMessage());
    }

    @ExceptionHandler(DomainException.class)
    ProblemDetail errorDeDominio(DomainException e) {
        var problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getError().getMensaje());
        problema.setProperty("codigo", e.getError().name());
        return problema;
    }

    // IllegalArgumentException de librerías (por ejemplo un enum con un valor que no existe):
    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail datosInvalidos(IllegalArgumentException e) {
        log.warn("Solicitud inválida: {}", e.getMessage());
        return problema(HttpStatus.BAD_REQUEST, ErrorGeneral.SOLICITUD_INVALIDA, ErrorGeneral.SOLICITUD_INVALIDA.mensaje());
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail inesperado(Exception e) {
        log.error("Error no controlado", e);
        return problema(HttpStatus.INTERNAL_SERVER_ERROR, ErrorGeneral.ERROR_INTERNO, ErrorGeneral.ERROR_INTERNO.mensaje());
    }

    private static ProblemDetail problema(HttpStatus estado, CodigoError error, String detalle) {
        var problema = ProblemDetail.forStatusAndDetail(estado, detalle);
        problema.setProperty("codigo", error.name());
        return problema;
    }
}
