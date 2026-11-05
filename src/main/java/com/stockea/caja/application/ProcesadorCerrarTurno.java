package com.stockea.caja.application;

import tools.jackson.core.JacksonException;
import com.stockea.shared.domain.ReglaNegocioException;
import com.stockea.caja.domain.ErrorCaja;
import com.stockea.sync.api.ComandoOperacion;
import com.stockea.sync.api.EstadoOperacion;
import com.stockea.sync.api.ProcesadorOperacion;
import com.stockea.sync.api.ResultadoOperacion;
import com.stockea.sync.api.TipoOperacion;
import com.stockea.ventas.api.ConsultaVentasApi;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.UUID;

// Cierre de caja: el cajero cuenta el efectivo y el servidor calcula el cuadre.
// Como la cola es FIFO por dispositivo, cuando llega el cierre ya llegaron todas las ventas del turno.
@Service
class ProcesadorCerrarTurno implements ProcesadorOperacion {

    private final JdbcTemplate jdbc;
    private final ConsultaVentasApi ventas;
    private final JsonMapper jsonMapper;

    ProcesadorCerrarTurno(JdbcTemplate jdbc, ConsultaVentasApi ventas, JsonMapper jsonMapper) {
        this.jdbc = jdbc;
        this.ventas = ventas;
        this.jsonMapper = jsonMapper;
    }

    record CerrarTurnoPayload(UUID turnoId, BigDecimal montoContado) {}

    @Override
    public TipoOperacion tipo() {
        return TipoOperacion.CERRAR_TURNO;
    }

    @Override
    public ResultadoOperacion procesar(ComandoOperacion cmd) {
        CerrarTurnoPayload p;
        try {
            p = jsonMapper.readValue(cmd.payloadJson(), CerrarTurnoPayload.class);
        } catch (JacksonException e) {
            throw new ReglaNegocioException(ErrorCaja.CIERRE_INVALIDO);
        }
        if (p.turnoId() == null || p.montoContado() == null || p.montoContado().signum() < 0) {
            throw new ReglaNegocioException(ErrorCaja.CIERRE_INVALIDO);
        }
        var inicial = jdbc.query("SELECT monto_inicial FROM turno_caja WHERE negocio_id = ? AND id = ? FOR UPDATE",
                (rs, i) -> rs.getBigDecimal(1), cmd.negocioId(), p.turnoId());
        if (inicial.isEmpty()) {
            return new ResultadoOperacion(cmd.operacionId(), EstadoOperacion.RECHAZADA_SIN_TURNO, null);
        }

        var resumen = ventas.resumenTurno(cmd.negocioId(), p.turnoId());
        var esperado = inicial.getFirst().add(resumen.efectivo());
        var diferencia = p.montoContado().subtract(esperado);

        jdbc.update("""
                UPDATE turno_caja
                SET estado = 'CERRADO', cerrado_en = ?, monto_contado = ?, efectivo_esperado = ?,
                    diferencia = ?, ventas_cantidad = ?
                WHERE negocio_id = ? AND id = ? AND estado = 'ABIERTO'
                """, java.sql.Timestamp.from(cmd.fechaCliente()), p.montoContado(), esperado, diferencia,
                resumen.ventas(), cmd.negocioId(), p.turnoId());
        return new ResultadoOperacion(cmd.operacionId(), EstadoOperacion.ACEPTADA, p.turnoId());
    }
}
