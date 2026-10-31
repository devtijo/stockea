package com.stockea.ventas.infrastructure.persistence;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "asignacion_lote")
public class AsignacionLoteEntity {

    @Id
    private UUID id;

    @Column(name = "negocio_id", nullable = false)
    private UUID negocioId;

    @Column(name = "item_venta_id", nullable = false)
    private UUID itemVentaId;

    @Column(name = "lote_id", nullable = false)
    private UUID loteId;

    @Column(nullable = false)
    private BigDecimal cantidad;

    @Column(name = "descuento_id")
    private UUID descuentoId;

    @Column(name = "monto_descuento", nullable = false)
    private BigDecimal montoDescuento;

    @Column(nullable = false, length = 20)
    private String origen;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getNegocioId() { return negocioId; }
    public void setNegocioId(UUID negocioId) { this.negocioId = negocioId; }
    public UUID getItemVentaId() { return itemVentaId; }
    public void setItemVentaId(UUID itemVentaId) { this.itemVentaId = itemVentaId; }
    public UUID getLoteId() { return loteId; }
    public void setLoteId(UUID loteId) { this.loteId = loteId; }
    public BigDecimal getCantidad() { return cantidad; }
    public void setCantidad(BigDecimal cantidad) { this.cantidad = cantidad; }
    public UUID getDescuentoId() { return descuentoId; }
    public void setDescuentoId(UUID descuentoId) { this.descuentoId = descuentoId; }
    public BigDecimal getMontoDescuento() { return montoDescuento; }
    public void setMontoDescuento(BigDecimal montoDescuento) { this.montoDescuento = montoDescuento; }
    public String getOrigen() { return origen; }
    public void setOrigen(String origen) { this.origen = origen; }
}