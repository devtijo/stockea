package com.stockea.sync.infrastructure.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record SyncRequest(
        @NotNull @Valid List<OperacionDto> operaciones
) {
    public record OperacionDto(
            @NotNull UUID operacionId,
            @NotNull Long secuenciaDispositivo,
            @NotNull String tipo,
            @NotNull java.time.Instant fechaCliente,
            @NotNull String payloadJson
    ) {}

    public record VentaPayloadDto(
            String metodoPago,
            BigDecimal montoRecibido,
            BigDecimal totalCliente,
            List<ItemDto> items
    ) {}

    public record ItemDto(
            UUID productoId,
            BigDecimal cantidad,
            BigDecimal precioUnitario,
            List<LoteLocalDto> lotesLocales
    ) {}

    public record LoteLocalDto(
            UUID loteId,
            BigDecimal cantidad
    ) {}
}