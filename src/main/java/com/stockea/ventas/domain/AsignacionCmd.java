package com.stockea.ventas.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record AsignacionCmd (UUID loteId, BigDecimal cantidad, UUID descuentoId, BigDecimal montoDescuento){
}
