package com.stockea.inventario.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.stockea.descuentos.api.DescuentosApi;
import com.stockea.shared.tenant.TenantContext;
import com.stockea.shared.tiempo.FechaNegocio;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Lo que pasa ANTES de vender: crear productos, cambiar precios y recibir mercancía
// Son operaciones de gestión: requieren internet y rol SUPERVISOR o más
@Service
@PreAuthorize("hasRole('SUPERVISOR')")
public class InventarioService {

    private final JdbcTemplate jdbc;
    private final DescuentosApi descuentos;
    private final Clock clock;
    private final ZoneId zona;

    public InventarioService(JdbcTemplate jdbc, DescuentosApi descuentos, Clock clock,
                             @Value("${stockea.zona-negocio}") String zona) {
        this.jdbc = jdbc;
        this.descuentos = descuentos;
        this.clock = clock;
        this.zona = ZoneId.of(zona);
    }

    public record NuevoProducto(String nombre, String categoria, String unidadVenta, BigDecimal precio) {
    }

    public record Entrada(UUID productoId, BigDecimal cantidad, LocalDate fechaVencimiento, BigDecimal costoUnitario) {
    }

    public record LoteResumen(UUID id, LocalDate fechaVencimiento, BigDecimal cantidadInicial,
                              BigDecimal cantidadDisponible, BigDecimal costoUnitario, String estado,
                              UUID descuentoId, BigDecimal descuentoPorcentaje) {
    }

    public record ProductoResumen(UUID id, String nombre, String categoria, String unidadVenta, BigDecimal precio,
                                  boolean activo, BigDecimal stock, List<LoteResumen> lotes) {
    }

    @Transactional
    public UUID crearProducto(NuevoProducto p) {
        var tenant = TenantContext.requerido();
        validarPrecio(p.precio());
        var id = UuidCreator.getTimeOrderedEpoch();
        jdbc.update("""
                INSERT INTO producto (id, negocio_id, nombre, categoria, unidad_venta, precio_venta)
                VALUES (?, ?, ?, ?, ?, ?)""",
                id, tenant.negocioId(), p.nombre().trim(), p.categoria(), p.unidadVenta(), p.precio());
        registrarPrecio(tenant.negocioId(), id, p.precio(), tenant.usuarioId());
        return id;
    }

    // El precio nuevo rige desde ahora
    // así una venta hecha sin internet antes del cambio se valida con el precio de su momento
    @Transactional
    public void cambiarPrecio(UUID productoId, BigDecimal precio) {
        var tenant = TenantContext.requerido();
        validarPrecio(precio);
        int filas = jdbc.update("UPDATE producto SET precio_venta = ?, actualizado_en = now() WHERE id = ?",
                precio, productoId);
        if (filas == 0) {
            throw new IllegalArgumentException("El producto no existe");
        }
        registrarPrecio(tenant.negocioId(), productoId, precio, tenant.usuarioId());
    }

    @Transactional
    public void cambiarEstado(UUID productoId, boolean activo) {
        if (jdbc.update("UPDATE producto SET activo = ?, actualizado_en = now() WHERE id = ?", activo, productoId) == 0) {
            throw new IllegalArgumentException("El producto no existe");
        }
    }

    // Entrada de mercancía: un lote nuevo con su fecha de vencimiento (FEFO lo usará en orden)
    @Transactional
    public UUID registrarEntrada(Entrada e) {
        var tenant = TenantContext.requerido();
        if (e.cantidad() == null || e.cantidad().signum() <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor que cero");
        }
        if (e.costoUnitario() == null || e.costoUnitario().signum() < 0) {
            throw new IllegalArgumentException("El costo no puede ser negativo");
        }
        if (e.fechaVencimiento() == null || e.fechaVencimiento().isBefore(hoy())) {
            throw new IllegalArgumentException("No se puede recibir mercancía ya vencida");
        }
        var activo = jdbc.queryForList("SELECT activo FROM producto WHERE id = ?", Boolean.class, e.productoId());
        if (activo.isEmpty() || !activo.getFirst()) {
            throw new IllegalArgumentException("El producto no existe o está inactivo");
        }
        var lote = UuidCreator.getTimeOrderedEpoch();
        jdbc.update("""
                INSERT INTO lote (id, negocio_id, producto_id, fecha_vencimiento, cantidad_inicial,
                                  cantidad_disponible, costo_unitario)
                VALUES (?, ?, ?, ?, ?, ?, ?)""",
                lote, tenant.negocioId(), e.productoId(), Date.valueOf(e.fechaVencimiento()), e.cantidad(),
                e.cantidad(), e.costoUnitario());
        jdbc.update("""
                INSERT INTO movimiento_inventario (id, negocio_id, lote_id, tipo, cantidad, creado_por)
                VALUES (?, ?, ?, 'ENTRADA', ?, ?)""",
                UuidCreator.getTimeOrderedEpoch(), tenant.negocioId(), lote, e.cantidad(), tenant.usuarioId());
        return lote;
    }

    @Transactional(readOnly = true)
    public List<ProductoResumen> productos() {
        var negocio = TenantContext.requerido().negocioId();
        var hoy = hoy();
        Map<UUID, ProductoResumen> porId = new LinkedHashMap<>();
        jdbc.query("""
                SELECT id, nombre, categoria, unidad_venta, precio_venta, activo
                  FROM producto ORDER BY activo DESC, nombre""",
                rs -> {
                    var id = rs.getObject("id", UUID.class);
                    porId.put(id, new ProductoResumen(id, rs.getString("nombre"), rs.getString("categoria"),
                            rs.getString("unidad_venta"), rs.getBigDecimal("precio_venta"), rs.getBoolean("activo"),
                            BigDecimal.ZERO, new ArrayList<>()));
                });
        // Solo lotes con algo disponible: los agotados no aportan a la gestión diaria
        List<Object[]> lotes = jdbc.query("""
                SELECT id, producto_id, fecha_vencimiento, cantidad_inicial, cantidad_disponible, costo_unitario, estado
                  FROM lote WHERE cantidad_disponible > 0 ORDER BY fecha_vencimiento, id""",
                (rs, i) -> new Object[]{rs.getObject("id", UUID.class), rs.getObject("producto_id", UUID.class),
                        rs.getDate("fecha_vencimiento").toLocalDate(), rs.getBigDecimal("cantidad_inicial"),
                        rs.getBigDecimal("cantidad_disponible"), rs.getBigDecimal("costo_unitario"),
                        rs.getString("estado")});
        var conDescuento = descuentos.vigentesPorLote(negocio, lotes.stream().map(l -> (UUID) l[0]).toList());
        List<ProductoResumen> resultado = new ArrayList<>();
        for (var p : porId.values()) {
            var propios = new ArrayList<LoteResumen>();
            var stock = BigDecimal.ZERO;
            for (var l : lotes) {
                if (!p.id().equals(l[1])) {
                    continue;
                }
                var vence = (LocalDate) l[2];
                var vencido = vence.isBefore(hoy);
                var d = conDescuento.get((UUID) l[0]);
                propios.add(new LoteResumen((UUID) l[0], vence, (BigDecimal) l[3], (BigDecimal) l[4],
                        (BigDecimal) l[5], vencido ? "VENCIDO" : (String) l[6],
                        d == null ? null : d.id(), d == null ? null : d.porcentaje()));
                if (!vencido && "ACTIVO".equals(l[6])) {
                    stock = stock.add((BigDecimal) l[4]);
                }
            }
            resultado.add(new ProductoResumen(p.id(), p.nombre(), p.categoria(), p.unidadVenta(), p.precio(),
                    p.activo(), stock, propios));
        }
        return resultado;
    }

    private void registrarPrecio(UUID negocio, UUID producto, BigDecimal precio, UUID usuario) {
        jdbc.update("""
                INSERT INTO historial_precio (id, negocio_id, producto_id, precio_venta, vigente_desde, creado_por)
                VALUES (?, ?, ?, ?, ?, ?)""",
                UuidCreator.getTimeOrderedEpoch(), negocio, producto, precio, Timestamp.from(clock.instant()), usuario);
    }

    private static void validarPrecio(BigDecimal precio) {
        if (precio == null || precio.signum() <= 0) {
            throw new IllegalArgumentException("El precio debe ser mayor que cero");
        }
    }

    private LocalDate hoy() {
        return FechaNegocio.calcular(clock.instant(), clock, zona);
    }
}
