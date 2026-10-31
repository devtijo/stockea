package com.stockea.inventario.domain;

import com.stockea.inventario.api.ResultadoAsignacion;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AsignadorFefoTest {

    private final AsignadorFefo asignador = new AsignadorFefo();
    private final UUID loteA = UUID.randomUUID();
    private final UUID loteB = UUID.randomUUID();
    private final LocalDate venceA = LocalDate.of(2026, 11, 5);
    private final LocalDate venceB = LocalDate.of(2026, 11, 15);

    @Test
    void unSoloLoteAlcanza() {
        List<LoteDisponible> lotes = List.of(
                new LoteDisponible(loteA, new BigDecimal("10.0"), venceA)
        );
        ResultadoAsignacion resultado = asignador.asignar(lotes, new BigDecimal("5.0"));

        assertEquals(1, resultado.fracciones().size());
        assertEquals(0, resultado.fracciones().get(0).cantidad().compareTo(new BigDecimal("5.0")));
        assertEquals(0, resultado.sinCobertura().compareTo(BigDecimal.ZERO));
    }

    @Test
    void fraccionaEnDosLotes() {
        List<LoteDisponible> lotes = List.of(
                new LoteDisponible(loteA, new BigDecimal("7.0"), venceA),
                new LoteDisponible(loteB, new BigDecimal("3.0"), venceB)
        );
        ResultadoAsignacion resultado = asignador.asignar(lotes, new BigDecimal("10.0"));

        assertEquals(2, resultado.fracciones().size());
        assertEquals(0, resultado.fracciones().get(0).cantidad().compareTo(new BigDecimal("7.0")));
        assertEquals(0, resultado.fracciones().get(1).cantidad().compareTo(new BigDecimal("3.0")));
        assertEquals(0, resultado.sinCobertura().compareTo(BigDecimal.ZERO));
    }

    @Test
    void sinCoberturaTotal() {
        List<LoteDisponible> lotes = List.of(
                new LoteDisponible(loteA, new BigDecimal("5.0"), venceA)
        );
        ResultadoAsignacion resultado = asignador.asignar(lotes, new BigDecimal("10.0"));

        assertEquals(1, resultado.fracciones().size());
        assertEquals(0, resultado.fracciones().get(0).cantidad().compareTo(new BigDecimal("5.0")));
        assertEquals(0, resultado.sinCobertura().compareTo(new BigDecimal("5.0")));
    }

    @Test
    void cantidadInvalidaLanzaExcepcion() {
        List<LoteDisponible> lotes = List.of(
                new LoteDisponible(loteA, new BigDecimal("10.0"), venceA)
        );
        assertThrows(RuntimeException.class, () -> asignador.asignar(lotes, BigDecimal.ZERO));
    }
}