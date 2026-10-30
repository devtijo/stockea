package com.stockea.ventas.domain;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class VentaTest {

    @Test
    void calculaCambioEnEfectivoYEstadoAceptada() {
        var cmd = new RegistrarVentaCmd(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                MetodoPago.EFECTIVO,
                new BigDecimal("40000"), BigDecimal.ZERO, new BigDecimal("40000"), // subtotal, desc, totalCliente
                new BigDecimal("50000"), // montoRecibido
                Instant.now(), List.of()
        );

        var item = new ItemVenta(UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("10"), new BigDecimal("4000"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of());

        Venta venta = Venta.registrar(cmd, List.of(item), Instant.now());

        assertEquals(new BigDecimal("10000"), venta.getCambio()); // 50000 - 40000
        assertEquals(EstadoVenta.ACEPTADA, venta.getEstado());
    }

    @Test
    void ventaConSinCoberturaEsDiscrepancia() {
        var cmd = new RegistrarVentaCmd(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                MetodoPago.EFECTIVO,
                new BigDecimal("40000"), BigDecimal.ZERO, new BigDecimal("40000"),
                new BigDecimal("40000"),
                Instant.now(), List.of()
        );

        // 9 unidades cubiertas + 1 sin cobertura = 10 vendidas
        var asignacion = new AsignacionLote(
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("9"),
                null, BigDecimal.ZERO, OrigenAsignacion.DISPOSITIVO
        );
        var item = new ItemVenta(UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("10"), new BigDecimal("4000"),
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("1"), List.of(asignacion));

        Venta venta = Venta.registrar(cmd, List.of(item), Instant.now());

        assertEquals(EstadoVenta.DISCREPANCIA, venta.getEstado());
    }
}