package com.stockea.sync.domain;

import com.stockea.shared.domain.DomainException;
import com.stockea.shared.domain.ErrorDominio;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class Discrepancia {
    private final UUID id;
    private final UUID negocioId;
    private final UUID operacionId;
    private final UUID ventaId;
    private final UUID itemVentaId;
    private final UUID productoId;
    private final UUID loteId;  // puede ser null
    private final MotivoDiscrepancia motivo;
    private final BigDecimal cantidadFaltante;
    private final BigDecimal precioCobrado;
    private final BigDecimal precioEsperado;
    private final Instant detectadaEn;

    private EstadoDiscrepancia estado;
    private UUID resueltaPor;
    private Instant resueltaEn;
    private UUID movimientoCorreccionId;

    public Discrepancia(UUID id, UUID negocioId, UUID operacionId, UUID ventaId,
                        UUID itemVentaId, UUID productoId, UUID loteId,
                        MotivoDiscrepancia motivo, BigDecimal cantidadFaltante,
                        BigDecimal precioCobrado, BigDecimal precioEsperado,
                        Instant detectadaEn) {
        this.id = Objects.requireNonNull(id);
        this.negocioId = Objects.requireNonNull(negocioId);
        this.operacionId = Objects.requireNonNull(operacionId);
        this.ventaId = Objects.requireNonNull(ventaId);
        this.itemVentaId = itemVentaId;
        this.productoId = Objects.requireNonNull(productoId);
        this.loteId = loteId;
        this.motivo = Objects.requireNonNull(motivo);
        this.cantidadFaltante = cantidadFaltante;
        this.precioCobrado = precioCobrado;
        this.precioEsperado = precioEsperado;
        this.detectadaEn = Objects.requireNonNull(detectadaEn);
        this.estado = EstadoDiscrepancia.ABIERTA;
    }

    public void resolver(UUID usuarioId, UUID movimientoId, Instant cuando) {
        if (this.estado == EstadoDiscrepancia.RESUELTA) {
            throw new DomainException(ErrorDominio.DISCREPANCIA_RESUELTA);
        }
        this.estado = EstadoDiscrepancia.RESUELTA;
        this.resueltaPor = Objects.requireNonNull(usuarioId);
        this.movimientoCorreccionId = Objects.requireNonNull(movimientoId);
        this.resueltaEn = Objects.requireNonNull(cuando);
    }

    // Getters
    public UUID getId() { return id; }
    public UUID getNegocioId() { return negocioId; }
    public UUID getOperacionId() { return operacionId; }
    public UUID getVentaId() { return ventaId; }
    public MotivoDiscrepancia getMotivo() { return motivo; }
    public BigDecimal getCantidadFaltante() { return cantidadFaltante; }
    public EstadoDiscrepancia getEstado() { return estado; }
    public UUID getResueltaPor() { return resueltaPor; }
    public Instant getResueltaEn() { return resueltaEn; }
}