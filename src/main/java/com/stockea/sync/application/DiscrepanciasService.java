package com.stockea.sync.application;

import com.stockea.shared.domain.ReglaNegocioException;
import com.stockea.sync.domain.ErrorSync;
import com.stockea.shared.tenant.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Bandeja del supervisor: las ventas se aceptaron igual (ADR-002), aquí se revisa qué pasó y se deja nota
@Service
@PreAuthorize("hasRole('SUPERVISOR')")
public class DiscrepanciasService {

    private final JdbcTemplate jdbc;

    DiscrepanciasService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Vista(UUID id, UUID ventaId, UUID productoId, String motivo, BigDecimal cantidadFaltante,
                        BigDecimal precioCobrado, BigDecimal precioEsperado, String estado,
                        Instant detectadaEn, Instant resueltaEn, String notaResolucion) {}

    @Transactional(readOnly = true)
    public List<Vista> listar(String estado) {
        return jdbc.query("""
                SELECT id, venta_id, producto_id, motivo, cantidad_faltante, precio_cobrado, precio_esperado,
                       estado, detectada_en, resuelta_en, nota_resolucion
                FROM discrepancia WHERE estado = ? ORDER BY detectada_en DESC LIMIT 200
                """, (rs, i) -> new Vista(
                rs.getObject("id", UUID.class), rs.getObject("venta_id", UUID.class),
                rs.getObject("producto_id", UUID.class), rs.getString("motivo"),
                rs.getBigDecimal("cantidad_faltante"), rs.getBigDecimal("precio_cobrado"),
                rs.getBigDecimal("precio_esperado"), rs.getString("estado"),
                rs.getTimestamp("detectada_en").toInstant(),
                rs.getTimestamp("resuelta_en") == null ? null : rs.getTimestamp("resuelta_en").toInstant(),
                rs.getString("nota_resolucion")), estado);
    }

    // Resolver = el supervisor revisó y explica qué hizo (conteo físico, cobro al cliente, etc.)
    @Transactional
    public void resolver(UUID id, String nota) {
        var tenant = TenantContext.requerido();
        int filas = jdbc.update("""
                UPDATE discrepancia SET estado = 'RESUELTA', resuelta_por = ?, resuelta_en = now(), nota_resolucion = ?
                WHERE id = ? AND estado = 'ABIERTA'
                """, tenant.usuarioId(), nota, id);
        if (filas == 0) {
            throw new ReglaNegocioException(ErrorSync.DISCREPANCIA_NO_ENCONTRADA);
        }
    }
}
