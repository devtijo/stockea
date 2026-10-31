package com.stockea.sync.api;

import java.math.BigDecimal;
import java.util.UUID;

public record ResultadoOperacion(
        UUID operacionId,
        EstadoOperacion estado,
        UUID resultadoId,
        DiscrepanciaInfo discrepancia
) {
    public ResultadoOperacion(UUID operacionId, EstadoOperacion estado, UUID resultadoId) {
        this(operacionId, estado, resultadoId, null);
    }

    public record DiscrepanciaInfo(
            UUID productoId,
            UUID loteId,
            BigDecimal cantidadFaltante,
            BigDecimal precioCobrado,
            BigDecimal precioEsperado
    ) {}
}