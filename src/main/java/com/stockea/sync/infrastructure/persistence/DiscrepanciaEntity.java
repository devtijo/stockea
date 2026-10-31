package com.stockea.sync.infrastructure.persistence;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "discrepancia")
public class DiscrepanciaEntity {

    @Id
    private UUID id;

    @Column(name = "negocio_id", nullable = false)
    private UUID negocioId;

    @Column(name = "operacion_id", nullable = false)
    private UUID operacionId;

    @Column(name = "venta_id", nullable = false)
    private UUID ventaId;

    @Column(name = "item_venta_id")
    private UUID itemVentaId;

    @Column(name = "producto_id", nullable = false)
    private UUID productoId;

    @Column(name = "lote_id")
    private UUID loteId;

    @Column(nullable = false, length = 25)
    private String motivo;

    @Column(name = "cantidad_faltante", nullable = false)
    private BigDecimal cantidadFaltante;

    @Column(name = "precio_cobrado")
    private BigDecimal precioCobrado;

    @Column(name = "precio_esperado")
    private BigDecimal precioEsperado;

    @Column(nullable = false, length = 10)
    private String estado;

    @Column(name = "detectada_en", nullable = false)
    private Instant detectadaEn;

    // Getters y setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getNegocioId() { return negocioId; }
    public void setNegocioId(UUID negocioId) { this.negocioId = negocioId; }
    public UUID getOperacionId() { return operacionId; }
    public void setOperacionId(UUID operacionId) { this.operacionId = operacionId; }
    public UUID getVentaId() { return ventaId; }
    public void setVentaId(UUID ventaId) { this.ventaId = ventaId; }
    public UUID getItemVentaId() { return itemVentaId; }
    public void setItemVentaId(UUID itemVentaId) { this.itemVentaId = itemVentaId; }
    public UUID getProductoId() { return productoId; }
    public void setProductoId(UUID productoId) { this.productoId = productoId; }
    public UUID getLoteId() { return loteId; }
    public void setLoteId(UUID loteId) { this.loteId = loteId; }
    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }
    public BigDecimal getCantidadFaltante() { return cantidadFaltante; }
    public void setCantidadFaltante(BigDecimal cantidadFaltante) { this.cantidadFaltante = cantidadFaltante; }
    public BigDecimal getPrecioCobrado() { return precioCobrado; }
    public void setPrecioCobrado(BigDecimal precioCobrado) { this.precioCobrado = precioCobrado; }
    public BigDecimal getPrecioEsperado() { return precioEsperado; }
    public void setPrecioEsperado(BigDecimal precioEsperado) { this.precioEsperado = precioEsperado; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public Instant getDetectadaEn() { return detectadaEn; }
    public void setDetectadaEn(Instant detectadaEn) { this.detectadaEn = detectadaEn; }
}