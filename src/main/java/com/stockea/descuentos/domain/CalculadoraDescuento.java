package com.stockea.descuentos.domain;

import com.stockea.shared.domain.DomainException;
import com.stockea.shared.domain.ErrorDominio;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class CalculadoraDescuento {
    public BigDecimal calcularMontoPorFraccion(BigDecimal cantidad,
                                               BigDecimal precioUnitario,
                                               BigDecimal porcentajeDescuento) {
        if (cantidad == null || cantidad.signum() <= 0) {
            throw new DomainException(ErrorDominio.CANTIDAD_FRACCION_INVALIDA);
        }
        if (precioUnitario == null || precioUnitario.signum() < 0) {
            throw new DomainException(ErrorDominio.PRECIO_UNITARIO_INVALIDO);
        }
        if (porcentajeDescuento == null || porcentajeDescuento.signum() < 0) {
            throw new DomainException(ErrorDominio.PORCENTAJE_DESCUENTO_INVALIDO);
        }
        if (porcentajeDescuento.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal subtotal = cantidad.multiply(precioUnitario);
        BigDecimal descuentoBruto = subtotal
                .multiply(porcentajeDescuento)
                .divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);

        return descuentoBruto.setScale(2, RoundingMode.HALF_UP);
    }
}