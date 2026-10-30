package com.stockea.inventario.api;

import com.stockea.shared.domain.DomainException;
import com.stockea.shared.domain.ErrorDominio;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

public record ResultadoAsignacion(List<FraccionLote> fracciones, BigDecimal sinCobertura) {
    public ResultadoAsignacion {
        fracciones = List.copyOf(Objects.requireNonNull(fracciones));
        if (sinCobertura == null || sinCobertura.signum() < 0) {
            throw new DomainException(ErrorDominio.SIN_COBERTURA_NEGATIVA);
        }
    }
}