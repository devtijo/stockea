package com.stockea.sync.api;

import java.util.UUID;

public record ResultadoOperacion(UUID operacionId, EstadoOperacion estado, UUID resultadoId) {}