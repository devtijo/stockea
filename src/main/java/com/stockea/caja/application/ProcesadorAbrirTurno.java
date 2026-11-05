package com.stockea.caja.application;

import tools.jackson.core.JacksonException;
import com.stockea.shared.domain.ReglaNegocioException;
import com.stockea.caja.domain.ErrorCaja;
import com.stockea.identidad.api.NegocioApi;
import com.stockea.sync.api.ComandoOperacion;
import com.stockea.sync.api.EstadoOperacion;
import com.stockea.sync.api.ProcesadorOperacion;
import com.stockea.sync.api.ResultadoOperacion;
import com.stockea.sync.api.TipoOperacion;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.UUID;

// El cajero abre la caja en el dispositivo (con o sin internet) y la apertura llega por la cola (ADR-025)
@Service
class ProcesadorAbrirTurno implements ProcesadorOperacion {

    private final JdbcTemplate jdbc;
    private final NegocioApi negocioApi;
    private final JsonMapper jsonMapper;

    ProcesadorAbrirTurno(JdbcTemplate jdbc, NegocioApi negocioApi, JsonMapper jsonMapper) {
        this.jdbc = jdbc;
        this.negocioApi = negocioApi;
        this.jsonMapper = jsonMapper;
    }

    record AbrirTurnoPayload(UUID turnoId, UUID cajeroId, BigDecimal montoInicial) {}

    @Override
    public TipoOperacion tipo() {
        return TipoOperacion.ABRIR_TURNO;
    }

    @Override
    public ResultadoOperacion procesar(ComandoOperacion cmd) {
        AbrirTurnoPayload p;
        try {
            p = jsonMapper.readValue(cmd.payloadJson(), AbrirTurnoPayload.class);
        } catch (JacksonException e) {
            throw new ReglaNegocioException(ErrorCaja.APERTURA_INVALIDA);
        }
        if (p.turnoId() == null || p.montoInicial() == null || p.montoInicial().signum() < 0) {
            throw new ReglaNegocioException(ErrorCaja.APERTURA_INVALIDA);
        }
        var cajero = negocioApi.esUsuarioDelNegocio(cmd.negocioId(), p.cajeroId()) ? p.cajeroId() : cmd.usuarioId();

        // ON CONFLICT: si el turno ya existe (reintento), no se toca
        jdbc.update("""
                INSERT INTO turno_caja (negocio_id, id, dispositivo_id, cajero_id, monto_inicial, abierto_en)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (negocio_id, id) DO NOTHING
                """, cmd.negocioId(), p.turnoId(), cmd.dispositivoId(), cajero, p.montoInicial(),
                java.sql.Timestamp.from(cmd.fechaCliente()));
        return new ResultadoOperacion(cmd.operacionId(), EstadoOperacion.ACEPTADA, p.turnoId());
    }
}
