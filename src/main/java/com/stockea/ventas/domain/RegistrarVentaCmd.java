package com.stockea.ventas.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RegistrarVentaCmd(UUID negocioId, UUID operacionId, UUID dispositivoId, UUID cajeroId, UUID turnoCajaId,
                                MetodoPago metodoPago, BigDecimal subtotalCliente, BigDecimal descuentoCliente, BigDecimal totalCliente,
                                BigDecimal montoRecibido, Instant fechaCliente, List<ItemVentaCmd> items
) {
}
