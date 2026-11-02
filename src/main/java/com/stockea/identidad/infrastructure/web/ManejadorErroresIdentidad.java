package com.stockea.identidad.infrastructure.web;

import com.stockea.identidad.api.DispositivoApi;
import com.stockea.identidad.domain.ReglaIdentidadException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class ManejadorErroresIdentidad {

    @ExceptionHandler(ReglaIdentidadException.class)
    ProblemDetail reglaIncumplida(ReglaIdentidadException e) {
        var estado = switch (e.error()) {
            case DOCUMENTO_YA_REGISTRADO -> HttpStatus.CONFLICT;
            case USUARIO_NO_ENCONTRADO -> HttpStatus.NOT_FOUND;
            case USUARIO_INHABILITADO -> HttpStatus.FORBIDDEN;
            default -> HttpStatus.BAD_REQUEST;
        };
        var problema = ProblemDetail.forStatusAndDetail(estado, e.getMessage());
        problema.setProperty("codigo", e.error().name());
        return problema;
    }

    @ExceptionHandler(DispositivoApi.AccesoRevocadoException.class)
    ProblemDetail accesoRevocado(DispositivoApi.AccesoRevocadoException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
    }
}
