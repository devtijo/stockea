package com.stockea.sync.api;

import java.time.Instant;
import java.util.UUID;

public record ComandoOperacion(
        UUID negocioId,
        UUID dispositivoId,
        UUID cajeroId,
        UUID turnoCajaId,
        UUID operacionId,
        Instant fechaCliente,
        String payloadJson
) {}