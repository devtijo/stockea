package com.stockea.inventario.domain;

import com.stockea.shared.domain.DomainException;
import com.stockea.shared.domain.ErrorDominio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record LoteDisponible(UUID id, BigDecimal cantidadDisponible, LocalDate fechaVencimiento) {
    public LoteDisponible {
        Objects.requireNonNull(id);
        Objects.requireNonNull(cantidadDisponible);
        Objects.requireNonNull(fechaVencimiento);
        if (cantidadDisponible.signum() < 0) {
            throw new DomainException(ErrorDominio.CANTIDAD_DISPONIBLE_NEGATIVA);
        }
    }
}