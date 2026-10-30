package com.stockea.shared.tiempo;

import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FechaNegocioTest {

    private final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    @Test
    void relojAdelantadoSeAcotaALaHoraDelServidor() {
        Clock relojServidor = Clock.fixed(Instant.parse("2026-10-30T15:00:00Z"), ZoneOffset.UTC);
        Instant fechaCliente = Instant.parse("2026-10-31T10:00:00Z");
        LocalDate resultado = FechaNegocio.calcular(fechaCliente, relojServidor, BOGOTA);

        assertEquals(LocalDate.of(2026, 10, 30), resultado);
    }

    @Test
    void bordeMedianoche() {
        Instant fechaCliente = Instant.parse("2026-10-30T04:30:00Z");
        Clock relojServidor = Clock.fixed(Instant.parse("2026-10-30T05:00:00Z"), ZoneOffset.UTC);
        LocalDate resultado = FechaNegocio.calcular(fechaCliente, relojServidor, BOGOTA);
        assertEquals(LocalDate.of(2026, 10, 29), resultado);
    }
}