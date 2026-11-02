package com.stockea.inventario.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventarioApi {
    ResultadoAsignacion asignarFefo(UUID negocioId, UUID productoId,
                                    BigDecimal cantidad, LocalDate fechaVenta);

    void registrarSalidaPorVenta(UUID negocioId, List<FraccionLote> fracciones, UUID operacionId, UUID usuarioId);

    Optional<BigDecimal> precioVigente(UUID negocioId, UUID productoId, Instant momento);
}
