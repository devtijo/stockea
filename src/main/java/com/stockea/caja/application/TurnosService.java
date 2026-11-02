package com.stockea.caja.application;

import com.stockea.identidad.api.NegocioApi;
import com.stockea.shared.tenant.TenantContext;
import com.stockea.ventas.api.ConsultaVentasApi;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Consulta de turnos para el supervisor: quién abrió, cuánto vendió y cómo cuadró
@Service
public class TurnosService {

    private final JdbcTemplate jdbc;
    private final ConsultaVentasApi ventas;
    private final NegocioApi negocio;

    TurnosService(JdbcTemplate jdbc, ConsultaVentasApi ventas, NegocioApi negocio) {
        this.jdbc = jdbc;
        this.ventas = ventas;
        this.negocio = negocio;
    }

    public record Turno(UUID id, UUID dispositivoId, UUID cajeroId, String cajero, String estado,
                        BigDecimal montoInicial, Instant abiertoEn, Instant cerradoEn,
                        BigDecimal montoContado, BigDecimal efectivoEsperado, BigDecimal diferencia,
                        ConsultaVentasApi.ResumenTurno ventas) {}

    private record Fila(UUID id, UUID dispositivoId, UUID cajeroId, String estado, BigDecimal montoInicial,
                        Instant abiertoEn, Instant cerradoEn, BigDecimal montoContado,
                        BigDecimal efectivoEsperado, BigDecimal diferencia) {}

    @PreAuthorize("hasRole('SUPERVISOR')")
    @Transactional(readOnly = true)
    public List<Turno> ultimos() {
        var negocioId = TenantContext.requerido().negocioId();
        var filas = jdbc.query("""
                SELECT id, dispositivo_id, cajero_id, estado, monto_inicial, abierto_en, cerrado_en,
                       monto_contado, efectivo_esperado, diferencia
                FROM turno_caja WHERE negocio_id = ? ORDER BY abierto_en DESC LIMIT 50
                """, (rs, i) -> new Fila(
                rs.getObject("id", UUID.class), rs.getObject("dispositivo_id", UUID.class),
                rs.getObject("cajero_id", UUID.class), rs.getString("estado"), rs.getBigDecimal("monto_inicial"),
                rs.getTimestamp("abierto_en").toInstant(),
                rs.getTimestamp("cerrado_en") == null ? null : rs.getTimestamp("cerrado_en").toInstant(),
                rs.getBigDecimal("monto_contado"), rs.getBigDecimal("efectivo_esperado"),
                rs.getBigDecimal("diferencia")), negocioId);

        var resumenes = ventas.resumenTurnos(negocioId, filas.stream().map(Fila::id).toList());
        var nombres = negocio.nombres(negocioId, filas.stream().map(Fila::cajeroId).distinct().toList());
        return filas.stream().map(f -> new Turno(f.id(), f.dispositivoId(), f.cajeroId(),
                nombres.getOrDefault(f.cajeroId(), "?"), f.estado(), f.montoInicial(), f.abiertoEn(),
                f.cerradoEn(), f.montoContado(), f.efectivoEsperado(), f.diferencia(),
                resumenes.getOrDefault(f.id(), ConsultaVentasApi.ResumenTurno.VACIO))).toList();
    }
}
