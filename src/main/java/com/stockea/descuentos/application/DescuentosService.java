package com.stockea.descuentos.application;

import com.stockea.descuentos.api.DescuentosApi;
import com.stockea.descuentos.domain.CalculadoraDescuento;
import com.stockea.identidad.api.NegocioApi;
import com.stockea.shared.tenant.TenantContext;
import com.github.f4b6a3.uuid.UuidCreator;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Descuentos por lote: el supervisor rebaja un lote
// La venta aplica el descuento del lote que FEFO realmente entrega, no el que el cliente supone.
@Service
public class DescuentosService implements DescuentosApi {

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate jdbcNombrado;
    private final NegocioApi negocio;
    private final CalculadoraDescuento calculadora = new CalculadoraDescuento();

    public DescuentosService(JdbcTemplate jdbc, NamedParameterJdbcTemplate jdbcNombrado, NegocioApi negocio) {
        this.jdbc = jdbc;
        this.jdbcNombrado = jdbcNombrado;
        this.negocio = negocio;
    }

    public record NuevoDescuento(UUID loteId, BigDecimal porcentaje) {
    }

    @PreAuthorize("hasRole('SUPERVISOR')")
    @Transactional
    public UUID crear(NuevoDescuento d) {
        var tenant = TenantContext.requerido();
        var maximo = negocio.porcentajeMaximoDescuento(tenant.negocioId());
        if (d.porcentaje() == null || d.porcentaje().signum() <= 0 || d.porcentaje().compareTo(maximo) > 0) {
            throw new IllegalArgumentException("El descuento debe estar entre 0 y " + maximo.stripTrailingZeros().toPlainString() + " %");
        }
        // Si el lote ya tenía descuento, se reemplaza (solo uno activo por lote)
        jdbc.update("UPDATE descuento SET activo = FALSE WHERE lote_id = ? AND activo", d.loteId());
        var id = UuidCreator.getTimeOrderedEpoch();
        try {
            // La FK (negocio_id, lote_id) rechaza un lote inexistente o de otro negocio
            jdbc.update("INSERT INTO descuento (id, negocio_id, lote_id, porcentaje, creado_por) VALUES (?,?,?,?,?)",
                    id, tenant.negocioId(), d.loteId(), d.porcentaje(), tenant.usuarioId());
        } catch (DataIntegrityViolationException e) {
            throw new IllegalArgumentException("El lote no existe en este negocio");
        }
        return id;
    }

    @PreAuthorize("hasRole('SUPERVISOR')")
    @Transactional
    public void desactivar(UUID descuentoId) {
        if (jdbc.update("UPDATE descuento SET activo = FALSE WHERE id = ? AND activo", descuentoId) == 0) {
            throw new IllegalArgumentException("El descuento no existe o ya estaba inactivo");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, DescuentoVigente> vigentesPorLote(UUID negocioId, Collection<UUID> lotes) {
        Map<UUID, DescuentoVigente> resultado = new HashMap<>();
        if (lotes == null || lotes.isEmpty()) {
            return resultado;
        }
        List<DescuentoVigente> filas = jdbcNombrado.query("""
                SELECT id, lote_id, porcentaje FROM descuento
                 WHERE negocio_id = :negocio AND activo AND lote_id IN (:lotes)""",
                Map.of("negocio", negocioId, "lotes", lotes),
                (rs, i) -> new DescuentoVigente(rs.getObject("id", UUID.class), rs.getObject("lote_id", UUID.class),
                        rs.getBigDecimal("porcentaje")));
        filas.forEach(f -> resultado.put(f.loteId(), f));
        return resultado;
    }

    @Override
    public BigDecimal calcularMonto(BigDecimal cantidad, BigDecimal precioUnitario, BigDecimal porcentaje) {
        return calculadora.calcularMontoPorFraccion(cantidad, precioUnitario, porcentaje);
    }
}
