package com.stockea.descuentos.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class CalculadoraDescuentoTest {

    private final CalculadoraDescuento calc = new CalculadoraDescuento();

    @Test
    void descuentoDel20PorCiento() {
        BigDecimal monto = calc.calcularMontoPorFraccion(
                new BigDecimal("1"),
                new BigDecimal("5000.00"),
                new BigDecimal("20.00")
        );
        assertEquals(new BigDecimal("1000.00"), monto);
    }

    @Test
    void sinDescuentoRetornaCero() {
        BigDecimal monto = calc.calcularMontoPorFraccion(
                new BigDecimal("3"),
                new BigDecimal("2500.00"),
                BigDecimal.ZERO
        );
        assertEquals(0, monto.compareTo(BigDecimal.ZERO));
    }

    @Test
    void redondeaHaciaArribaEnCentavos() {
        BigDecimal monto = calc.calcularMontoPorFraccion(
                new BigDecimal("1"),
                new BigDecimal("333.33"),
                new BigDecimal("10.00")
        );
        assertEquals(new BigDecimal("33.33"), monto);
    }

    @Test
    void porcentajeNegativoLanzaExcepcion() {
        assertThrows(RuntimeException.class, () -> calc.calcularMontoPorFraccion(
                new BigDecimal("1"),
                new BigDecimal("1000.00"),
                new BigDecimal("-5")
        ));
    }
}