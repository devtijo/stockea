package com.stockea.ventas.api;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

// Totales de ventas por turno, para el cuadre de caja
public interface ConsultaVentasApi {

    record ResumenTurno(long ventas, BigDecimal efectivo, BigDecimal transferencia,
                        BigDecimal tarjeta, BigDecimal total) {
        public static final ResumenTurno VACIO =
                new ResumenTurno(0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    ResumenTurno resumenTurno(UUID negocioId, UUID turnoId);

    Map<UUID, ResumenTurno> resumenTurnos(UUID negocioId, Collection<UUID> turnos);
}
