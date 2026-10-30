package com.stockea.sync.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record OperacionSync(
        UUID negocioId,
        UUID operacionId,
        UUID dispositivoId,
        UUID usuarioId,
        TipoOperacion tipo,
        long secuenciaDispositivo,
        EstadoOperacion estado,
        String payload,
        Instant creadaEnCliente,
        Instant recibidaEn,
        Instant procesadaEn,
        UUID resultadoId
) {
    public OperacionSync {
        Objects.requireNonNull(negocioId);
        Objects.requireNonNull(operacionId);
        Objects.requireNonNull(tipo);
        Objects.requireNonNull(estado);
    }
}