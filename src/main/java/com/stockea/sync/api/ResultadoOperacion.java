package com.stockea.sync.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ResultadoOperacion(
        UUID operacionId,
        EstadoOperacion estado,
        UUID resultadoId,
        List<DiscrepanciaInfo> discrepancias
) {
    public ResultadoOperacion {
        discrepancias = discrepancias == null ? List.of() : List.copyOf(discrepancias);
    }

    public ResultadoOperacion(UUID operacionId, EstadoOperacion estado, UUID resultadoId) {
        this(operacionId, estado, resultadoId, List.of());
    }

    public record DiscrepanciaInfo(
            UUID itemVentaId,
            UUID productoId,
            UUID loteId,
            BigDecimal cantidadFaltante,
            BigDecimal precioCobrado,
            BigDecimal precioEsperado
    ) {}
}
