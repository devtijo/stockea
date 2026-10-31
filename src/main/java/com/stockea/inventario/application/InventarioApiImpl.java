package com.stockea.inventario.application;

import com.stockea.inventario.api.FraccionLote;
import com.stockea.inventario.api.InventarioApi;
import com.stockea.inventario.api.ResultadoAsignacion;
import com.stockea.inventario.domain.AsignadorFefo;
import com.stockea.inventario.domain.LoteDisponible;
import com.stockea.inventario.infrastructure.persistence.LoteEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class InventarioApiImpl implements InventarioApi {

    private final EntityManager entityManager;
    private final AsignadorFefo asignadorFefo = new AsignadorFefo();

    public InventarioApiImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
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
}