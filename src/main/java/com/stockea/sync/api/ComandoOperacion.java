package com.stockea.sync.api;

import java.time.Instant;
import java.util.UUID;

// Una operación de la cola del dispositivo, lista para su procesador
public record ComandoOperacion(
        UUID negocioId,
        UUID dispositivoId,
        UUID usuarioId,
        UUID operacionId,
        Instant fechaCliente,
        String payloadJson
) {}