package com.stockea.inventario.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public interface InventarioApi {
    ResultadoAsignacion asignarFefo(UUID negocioId, UUID productoId,
                                    BigDecimal cantidad, LocalDate fechaVenta);
}