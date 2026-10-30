package com.stockea.ventas.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ItemVentaCmd(UUID productoId, BigDecimal cantidad,
                           BigDecimal precioUnitario, List<AsignacionCmd> asignaciones) {
}
