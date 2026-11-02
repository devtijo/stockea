package com.stockea.inventario.application;

import com.stockea.inventario.api.FraccionLote;
import com.stockea.inventario.api.InventarioApi;
import com.stockea.inventario.api.ResultadoAsignacion;
import com.stockea.inventario.domain.AsignadorFefo;
import com.stockea.inventario.domain.LoteDisponible;
import com.stockea.inventario.infrastructure.persistence.LoteEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import com.github.f4b6a3.uuid.UuidCreator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class InventarioApiImpl implements InventarioApi {

    private final EntityManager entityManager;
    private final JdbcTemplate jdbc;
    private final AsignadorFefo asignadorFefo = new AsignadorFefo();

    public InventarioApiImpl(EntityManager entityManager, JdbcTemplate jdbc) {
        this.entityManager = entityManager;
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public ResultadoAsignacion asignarFefo(UUID negocioId, UUID productoId,
                                           BigDecimal cantidad, LocalDate fechaVenta) {
        List<LoteEntity> lotes = entityManager.createQuery(
                        "SELECT l FROM LoteEntity l " +
                                "WHERE l.negocioId = :negocioId " +
                                "  AND l.productoId = :productoId " +
                                "  AND l.estado = 'ACTIVO' " +
                                "  AND l.cantidadDisponible > 0 " +
                                "  AND l.fechaVencimiento >= :fechaVenta " +
                                "ORDER BY l.fechaVencimiento ASC, l.id ASC",
                        LoteEntity.class)
                .setParameter("negocioId", negocioId)
                .setParameter("productoId", productoId)
                .setParameter("fechaVenta", fechaVenta)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultList();

        List<LoteDisponible> disponibles = lotes.stream()
                .map(l -> new LoteDisponible(l.getId(), l.getCantidadDisponible(), l.getFechaVencimiento()))
                .toList();

        ResultadoAsignacion resultado = asignadorFefo.asignar(disponibles, cantidad);

        // DESCUENTO REAL DEL STOCK
        Map<UUID, LoteEntity> porId = lotes.stream()
                .collect(Collectors.toMap(LoteEntity::getId, Function.identity()));

        for (FraccionLote f : resultado.fracciones()) {
            LoteEntity lote = porId.get(f.loteId());
            BigDecimal nuevo = lote.getCantidadDisponible().subtract(f.cantidad());
            lote.setCantidadDisponible(nuevo);
            if (nuevo.signum() == 0) lote.setEstado("AGOTADO");
        }

        return resultado;
    }

    @Override
    @Transactional
    public void registrarSalidaPorVenta(UUID negocioId, List<FraccionLote> fracciones, UUID operacionId,
                                        UUID usuarioId) {
        // Los lotes se modificaron con JPA: se envían a la base antes de escribir con JDBC
        entityManager.flush();
        for (FraccionLote f : fracciones) {
            jdbc.update("""
                    INSERT INTO movimiento_inventario (id, negocio_id, lote_id, tipo, cantidad, operacion_id, creado_por)
                    VALUES (?, ?, ?, 'VENTA', ?, ?, ?)""",
                    UuidCreator.getTimeOrderedEpoch(), negocioId, f.loteId(), f.cantidad(), operacionId, usuarioId);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BigDecimal> precioVigente(UUID negocioId, UUID productoId, Instant momento) {
        // El precio que regía en ese momento. Si la venta es anterior a todo el historial
        var precios = jdbc.queryForList("""
                SELECT precio_venta FROM historial_precio
                 WHERE negocio_id = ? AND producto_id = ?
                 ORDER BY (vigente_desde <= ?) DESC,
                          CASE WHEN vigente_desde <= ? THEN vigente_desde END DESC,
                          vigente_desde
                 LIMIT 1""",
                BigDecimal.class, negocioId, productoId, Timestamp.from(momento), Timestamp.from(momento));
        if (!precios.isEmpty()) {
            return Optional.of(precios.getFirst());
        }
        // Productos sin historial (datos viejos): el precio actual del producto
        return jdbc.queryForList("SELECT precio_venta FROM producto WHERE negocio_id = ? AND id = ?",
                BigDecimal.class, negocioId, productoId).stream().findFirst();
    }
}
