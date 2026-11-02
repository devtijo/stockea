package com.stockea.inventario.application;

import com.stockea.descuentos.api.DescuentosApi;
import com.stockea.shared.tenant.TenantContext;
import com.stockea.shared.tiempo.FechaNegocio;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Catálogo del negocio para cachear en el dispositivo:
// productos activos, precio vigente y stock vendible hoy (lotes activos no vencidos)
// Consulta de solo lectura; RLS garantiza que solo se ven filas del negocio
@Service
public class CatalogoService {

    // descuento: el del lote que FEFO venderá primero y cuántas unidades le quedan (null si no tiene)
    public record ProductoCatalogo(UUID id, String nombre, String unidadVenta, BigDecimal precio, BigDecimal stock,
                                   DescuentoCatalogo descuento) {
    }

    public record DescuentoCatalogo(BigDecimal porcentaje, BigDecimal unidades) {
    }

    private final JdbcTemplate jdbc;
    private final DescuentosApi descuentos;
    private final Clock clock;
    private final ZoneId zona;

    public CatalogoService(JdbcTemplate jdbc, DescuentosApi descuentos, Clock clock,
                           @Value("${stockea.zona-negocio}") String zona) {
        this.jdbc = jdbc;
        this.descuentos = descuentos;
        this.clock = clock;
        this.zona = ZoneId.of(zona);
    }

    @Transactional(readOnly = true)
    public List<ProductoCatalogo> catalogo() {
        var negocio = TenantContext.requerido().negocioId();
        var hoy = FechaNegocio.calcular(clock.instant(), clock, zona);
        // Primer lote que FEFO entregará por producto (vence antes y tiene stock)
        Map<UUID, Object[]> primerLote = new HashMap<>();
        jdbc.query("""
                SELECT DISTINCT ON (producto_id) producto_id, id, cantidad_disponible
                  FROM lote
                 WHERE estado = 'ACTIVO' AND cantidad_disponible > 0 AND fecha_vencimiento >= ?
                 ORDER BY producto_id, fecha_vencimiento, id""",
                rs -> {
                    primerLote.put(rs.getObject("producto_id", UUID.class),
                            new Object[]{rs.getObject("id", UUID.class), rs.getBigDecimal("cantidad_disponible")});
                }, hoy);
        var conDescuento = descuentos.vigentesPorLote(negocio,
                primerLote.values().stream().map(l -> (UUID) l[0]).toList());

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
                (rs, i) -> {
                    var id = rs.getObject("id", UUID.class);
                    var lote = primerLote.get(id);
                    var d = lote == null ? null : conDescuento.get((UUID) lote[0]);
                    return new ProductoCatalogo(id, rs.getString("nombre"), rs.getString("unidad_venta"),
                            rs.getBigDecimal("precio_venta"), rs.getBigDecimal("stock"),
                            d == null ? null : new DescuentoCatalogo(d.porcentaje(), (BigDecimal) lote[1]));
                },
                hoy, negocio);
    }
}
