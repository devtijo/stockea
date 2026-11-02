package com.stockea.ventas.application;

import com.stockea.ventas.api.ConsultaVentasApi;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
class ConsultaVentasService implements ConsultaVentasApi {

    private final NamedParameterJdbcTemplate jdbc;

    ConsultaVentasService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public ResumenTurno resumenTurno(UUID negocioId, UUID turnoId) {
        return resumenTurnos(negocioId, List.of(turnoId)).getOrDefault(turnoId, ResumenTurno.VACIO);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, ResumenTurno> resumenTurnos(UUID negocioId, Collection<UUID> turnos) {
        var resultado = new HashMap<UUID, ResumenTurno>();
        if (turnos.isEmpty()) return resultado;
        // Lo que se cobró es lo que entra a la caja, haya discrepancia o no (ADR-002)
        jdbc.query("""
                SELECT turno_caja_id,
                       COUNT(*) AS ventas,
                       COALESCE(SUM(total) FILTER (WHERE metodo_pago = 'EFECTIVO'), 0)      AS efectivo,
                       COALESCE(SUM(total) FILTER (WHERE metodo_pago = 'TRANSFERENCIA'), 0) AS transferencia,
                       COALESCE(SUM(total) FILTER (WHERE metodo_pago = 'TARJETA'), 0)       AS tarjeta,
                       COALESCE(SUM(total), 0) AS total
                FROM venta
                WHERE negocio_id = :negocio AND turno_caja_id IN (:turnos)
                GROUP BY turno_caja_id
                """, Map.of("negocio", negocioId, "turnos", turnos), rs -> {
            resultado.put(rs.getObject("turno_caja_id", UUID.class), new ResumenTurno(
                    rs.getLong("ventas"),
                    rs.getBigDecimal("efectivo"),
                    rs.getBigDecimal("transferencia"),
                    rs.getBigDecimal("tarjeta"),
                    rs.getBigDecimal("total")));
        });
        return resultado;
    }
}
