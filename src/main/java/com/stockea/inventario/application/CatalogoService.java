package com.stockea.inventario.application;

import com.stockea.shared.tenant.TenantContext;
import com.stockea.shared.tiempo.FechaNegocio;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

// Catálogo del negocio para cachear en el dispositivo:
// productos activos, precio vigente y stock vendible hoy (lotes activos no vencidos)
// Consulta de solo lectura; RLS garantiza que solo se ven filas del negocio
@Service
public class CatalogoService {

    public record ProductoCatalogo(UUID id, String nombre, String unidadVenta, BigDecimal precio, BigDecimal stock) {
    }

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final ZoneId zona;

    public CatalogoService(JdbcTemplate jdbc, Clock clock, @Value("${stockea.zona-negocio}") String zona) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.zona = ZoneId.of(zona);
    }

    @Transactional(readOnly = true)
    public List<ProductoCatalogo> catalogo() {
        var negocio = TenantContext.requerido().negocioId();
        var hoy = FechaNegocio.calcular(clock.instant(), clock, zona);
        return jdbc.query("""
                SELECT p.id, p.nombre, p.unidad_venta, p.precio_venta,
                       COALESCE(SUM(l.cantidad_disponible) FILTER (
                           WHERE l.estado = 'ACTIVO' AND l.fecha_vencimiento >= ?), 0) AS stock
                  FROM producto p
                  LEFT JOIN lote l ON l.producto_id = p.id AND l.negocio_id = p.negocio_id
                 WHERE p.negocio_id = ? AND p.activo
                 GROUP BY p.id, p.nombre, p.unidad_venta, p.precio_venta
                 ORDER BY p.nombre
                """,
                (rs, i) -> new ProductoCatalogo(rs.getObject("id", UUID.class), rs.getString("nombre"),
                        rs.getString("unidad_venta"), rs.getBigDecimal("precio_venta"), rs.getBigDecimal("stock")),
                hoy, negocio);
    }
}
