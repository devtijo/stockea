package com.stockea.inventario.domain;

import com.stockea.inventario.api.LoteDisponible;
import com.stockea.inventario.api.ResultadoAsignacion;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class AsignadorFefoTest {

    private final AsignadorFefo asignador = new AsignadorFefo();
    private final UUID loteA = UUID.randomUUID();
    private final UUID loteB = UUID.randomUUID();

    @Test
    void unSoloLoteAlcanza() {
        List<LoteDisponible> lotes = List.of(new LoteDisponible(loteA, new BigDecimal("10.0")));
        ResultadoAsignacion resultado = asignador.asignar(lotes, new BigDecimal("5.0"));

        assertEquals(1, resultado.fracciones().size());
        assertEquals(new BigDecimal("5.0"), resultado.fracciones().get(0).cantidad());
        assertEquals(0, resultado.sinCobertura().compareTo(BigDecimal.ZERO));
    }

    @Test
    void fraccionaEnDosLotes() {
        List<LoteDisponible> lotes = List.of(
                new LoteDisponible(loteA, new BigDecimal("7.0")),
                new LoteDisponible(loteB, new BigDecimal("3.0"))
        );
        ResultadoAsignacion resultado = asignador.asignar(lotes, new BigDecimal("10.0"));

        assertEquals(2, resultado.fracciones().size());
        assertEquals(new BigDecimal("7.0"), resultado.fracciones().get(0).cantidad());
        assertEquals(new BigDecimal("3.0"), resultado.fracciones().get(1).cantidad());
        assertEquals(0, resultado.sinCobertura().compareTo(BigDecimal.ZERO));
    }

    @Test
    void sinCoberturaTotal() {
        List<LoteDisponible> lotes = List.of(new LoteDisponible(loteA, new BigDecimal("5.0")));
        ResultadoAsignacion resultado = asignador.asignar(lotes, new BigDecimal("10.0"));

        assertEquals(1, resultado.fracciones().size());
        assertEquals(new BigDecimal("5.0"), resultado.fracciones().get(0).cantidad());
        assertEquals(new BigDecimal("5.0"), resultado.sinCobertura()); // Faltaron 5
    }

    @Test
    void cantidadInvalidaLanzaExcepcion() {
        List<LoteDisponible> lotes = List.of(new LoteDisponible(loteA, new BigDecimal("10.0")));
        assertThrows(RuntimeException.class, () -> asignador.asignar(lotes, BigDecimal.ZERO));
    }
}