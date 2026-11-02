package com.stockea.analitica.application;

import com.stockea.shared.seguridad.Rol;
import com.stockea.shared.tenant.TenantContext;
import com.stockea.ventas.api.VentaRegistrada;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

// Velocidad de venta = unidades vendidas por día en una ventana móvil
//Con ella y el stock actual se calculan los días de cobertura y se sugiere
//reponer  Se alimenta SOLO de eventos: no toca las tablas de ventas.
@Service
public class VelocidadVentaService {

    // El Worker no actúa en nombre de una persona: usa este usuario técnico
    static final UUID USUARIO_SISTEMA = new UUID(0L, 0L);

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final ZoneId zona;
    private final int diasAlerta;

    public VelocidadVentaService(JdbcTemplate jdbc, TransactionTemplate tx, Clock clock,
                                 @Value("${stockea.zona-negocio:America/Bogota}") String zona,
                                 @Value("${stockea.reposicion.dias-alerta:3}") int diasAlerta) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.clock = clock;
        this.zona = ZoneId.of(zona);
        this.diasAlerta = diasAlerta;
    }

    //Suma la venta a los acumulados diarios. Es IDEMPOTENTE: RabbitMQ entrega
    //"al menos una vez", Devuelve false si era un duplicado.

    public boolean registrar(VentaRegistrada evento) {
        var tenant = new TenantContext.Tenant(evento.negocioId(), USUARIO_SISTEMA, Rol.CAJERO);
        return TenantContext.ejecutarComo(tenant, () -> Boolean.TRUE.equals(tx.execute(estado -> {
            int nuevo = jdbc.update("""
                    INSERT INTO evento_consumido (negocio_id, evento, llave) VALUES (?, 'venta.registrada', ?)
                    ON CONFLICT DO NOTHING""", evento.negocioId(), evento.ventaId());
            if (nuevo == 0) {
                return false;
            }
            for (var item : evento.items()) {
                jdbc.update("""
                        INSERT INTO venta_diaria_producto (negocio_id, producto_id, fecha, unidades, ventas)
                        VALUES (?, ?, ?, ?, 1)
                        ON CONFLICT (negocio_id, producto_id, fecha) DO UPDATE
                           SET unidades = venta_diaria_producto.unidades + EXCLUDED.unidades,
                               ventas = venta_diaria_producto.ventas + 1,
                               actualizado_en = now()""",
                        evento.negocioId(), item.productoId(), Date.valueOf(evento.fechaVenta()), item.cantidad());
            }
            return true;
        })));
    }

    public record VelocidadProducto(UUID productoId, String nombre, BigDecimal unidadesVendidas,
                                    BigDecimal velocidadDiaria, BigDecimal stock, BigDecimal diasCobertura,
                                    String sugerencia) {
    }

    // Velocidad de cada producto activo del negocio en los últimos días
    @PreAuthorize("hasRole('SUPERVISOR')")
    public List<VelocidadProducto> consultar(int dias) {
        var hoy = LocalDate.now(clock.withZone(zona));
        var desde = hoy.minusDays(dias - 1L);
        var ventana = BigDecimal.valueOf(dias);
        return jdbc.query("""
                SELECT p.id, p.nombre,
                       COALESCE((SELECT SUM(v.unidades) FROM venta_diaria_producto v
                                  WHERE v.producto_id = p.id AND v.fecha BETWEEN ? AND ?), 0) AS unidades,
                       COALESCE((SELECT SUM(l.cantidad_disponible) FROM lote l
                                  WHERE l.producto_id = p.id AND l.estado = 'ACTIVO'
                                    AND l.fecha_vencimiento >= ?), 0) AS stock
                  FROM producto p
                 WHERE p.activo
                 ORDER BY p.nombre""",
                (rs, fila) -> {
                    var unidades = rs.getBigDecimal("unidades");
                    var stock = rs.getBigDecimal("stock");
                    var velocidad = unidades.divide(ventana, 2, RoundingMode.HALF_UP);
                    BigDecimal cobertura = velocidad.signum() == 0 ? null
                            : stock.divide(velocidad, 1, RoundingMode.HALF_UP);
                    return new VelocidadProducto(rs.getObject("id", UUID.class), rs.getString("nombre"),
                            unidades, velocidad, stock, cobertura, sugerencia(velocidad, cobertura));
                },
                Date.valueOf(desde), Date.valueOf(hoy), Date.valueOf(hoy));
    }

    private String sugerencia(BigDecimal velocidad, BigDecimal cobertura) {
        if (velocidad.signum() == 0) {
            return "SIN_MOVIMIENTO";
        }
        if (cobertura.compareTo(BigDecimal.valueOf(diasAlerta)) < 0) {
            return "REPONER";
        }
        return cobertura.compareTo(BigDecimal.valueOf(diasAlerta * 2L)) < 0 ? "VIGILAR" : "OK";
    }
}
