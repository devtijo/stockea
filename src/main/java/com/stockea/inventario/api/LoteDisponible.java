package com.stockea.inventario.api;

import com.stockea.shared.domain.DomainException;
import com.stockea.shared.domain.ErrorDominio;

import java.math.BigDecimal;
import java.util.UUID;

public record LoteDisponible(UUID id, BigDecimal cantidadDisponible) {
    public LoteDisponible {
        if (id == null) throw new DomainException(ErrorDominio.LOTE_ID_OBLIGATORIO);
        if (cantidadDisponible == null || cantidadDisponible.signum() < 0) {
            throw new DomainException(ErrorDominio.CANTIDAD_DISPONIBLE_NEGATIVA);
        }
    }
}