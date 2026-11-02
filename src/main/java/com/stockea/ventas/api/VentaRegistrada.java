package com.stockea.ventas.api;

import org.jmolecules.event.annotation.Externalized;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Externalized("stockea.ventas::venta.registrada")
public record VentaRegistrada(
    UUID negocioId,
    UUID ventaId,
    UUID operacionId,
    String estado,
    LocalDate fechaVenta,
    BigDecimal total,
    String metodoPago,
    Instant registradaEn,
    List<Item> items){

    public record Item(UUID productoId, BigDecimal cantidad){
    }

}
