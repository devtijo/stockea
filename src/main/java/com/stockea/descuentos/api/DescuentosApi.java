package com.stockea.descuentos.api;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public interface DescuentosApi {

    record DescuentoVigente(UUID id, UUID loteId, BigDecimal porcentaje) {
    }

    // Descuentos activos de los lotes pedidos
    Map<UUID, DescuentoVigente> vigentesPorLote(UUID negocioId, Collection<UUID> lotes);

    // Monto del descuento para una cantidad redondeada
    BigDecimal calcularMonto(BigDecimal cantidad, BigDecimal precioUnitario, BigDecimal porcentaje);
}
