package com.stockea.inventario.api;

import com.stockea.shared.domain.DomainException;
import com.stockea.shared.domain.ErrorDominio;
import java.math.BigDecimal;
import java.util.UUID;

public record FraccionLote(UUID loteId, BigDecimal cantidad) {
    public FraccionLote {
        if (loteId == null) throw new DomainException(ErrorDominio.LOTE_ID_OBLIGATORIO);
        if (cantidad == null || cantidad.signum() <= 0) {
            throw new DomainException(ErrorDominio.CANTIDAD_FRACCION_INVALIDA);
        }
    }
}