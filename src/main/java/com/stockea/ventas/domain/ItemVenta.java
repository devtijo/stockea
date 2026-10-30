package com.stockea.ventas.domain;

import com.stockea.shared.domain.DomainException;
import com.stockea.shared.domain.ErrorDominio;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record ItemVenta(
        UUID id, UUID productoId, BigDecimal cantidad, BigDecimal precioUnitario,
        BigDecimal descuentoTotalLinea, BigDecimal descuentoSinCobertura,
        BigDecimal cantidadSinCobertura, List<AsignacionLote> asignaciones
) {
    public ItemVenta {
        asignaciones = List.copyOf(Objects.requireNonNull(asignaciones));

        if (cantidad == null || cantidad.signum() <= 0) {
            throw new DomainException(ErrorDominio.CANTIDAD_FRACCION_INVALIDA);
        }
        if (cantidadSinCobertura == null || cantidadSinCobertura.signum() < 0) {
            throw new DomainException(ErrorDominio.CANTIDAD_DISPONIBLE_NEGATIVA);
        }

        BigDecimal sumaAsignaciones = asignaciones.stream()
                .map(AsignacionLote::cantidad)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCubierto = sumaAsignaciones.add(cantidadSinCobertura);

        if (totalCubierto.compareTo(cantidad) != 0) {
            throw new DomainException(ErrorDominio.INVARIANTE_ITEM_VENTA_ROTA);
        }
    }

    public boolean tieneDiscrepancia() {
        return cantidadSinCobertura.compareTo(BigDecimal.ZERO) > 0;
    }

    public BigDecimal calcularSubtotalLinea() {
        BigDecimal bruto = cantidad.multiply(precioUnitario);
        return bruto.subtract(descuentoTotalLinea);
    }
}