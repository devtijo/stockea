package com.stockea.ventas.domain;

import com.stockea.shared.domain.DomainException;
import com.stockea.shared.domain.ErrorDominio;
import java.math.BigDecimal;
import java.util.UUID;

public record AsignacionLote(
        UUID id, UUID loteId, BigDecimal cantidad, UUID descuentoId,
        BigDecimal montoDescuento, OrigenAsignacion origen
) {
    public AsignacionLote {
        if (cantidad == null || cantidad.signum() <= 0) {
            throw new DomainException(ErrorDominio.CANTIDAD_FRACCION_INVALIDA);
        }
    }
}