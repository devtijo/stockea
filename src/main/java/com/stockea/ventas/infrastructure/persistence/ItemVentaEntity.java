package com.stockea.ventas.infrastructure.persistence;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "item_venta")
public class ItemVentaEntity {

    @Id
    private UUID id;

    @Column(name = "negocio_id", nullable = false)
    private UUID negocioId;

    @Column(name = "venta_id", nullable = false)
    private UUID ventaId;

    @Column(name = "producto_id", nullable = false)
    private UUID productoId;

    @Column(nullable = false)
    private Short linea;

    @Column(nullable = false)
    private BigDecimal cantidad;

    @Column(name = "precio_unitario", nullable = false)
    private BigDecimal precioUnitario;

    @Column(name = "descuento_total_linea", nullable = false)
    private BigDecimal descuentoTotalLinea;

    @Column(name = "descuento_sin_cobertura", nullable = false)
    private BigDecimal descuentoSinCobertura;

    @Column(name = "cantidad_sin_cobertura", nullable = false)
    private BigDecimal cantidadSinCobertura;

    @Column(nullable = false)
    private BigDecimal subtotal;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getNegocioId() { return negocioId; }
    public void setNegocioId(UUID negocioId) { this.negocioId = negocioId; }
    public UUID getVentaId() { return ventaId; }
    public void setVentaId(UUID ventaId) { this.ventaId = ventaId; }
    public UUID getProductoId() { return productoId; }
    public void setProductoId(UUID productoId) { this.productoId = productoId; }
    public Short getLinea() { return linea; }
    public void setLinea(Short linea) { this.linea = linea; }
    public BigDecimal getCantidad() { return cantidad; }
    public void setCantidad(BigDecimal cantidad) { this.cantidad = cantidad; }
    public BigDecimal getPrecioUnitario() { return precioUnitario; }
    public void setPrecioUnitario(BigDecimal precioUnitario) { this.precioUnitario = precioUnitario; }
    public BigDecimal getDescuentoTotalLinea() { return descuentoTotalLinea; }
    public void setDescuentoTotalLinea(BigDecimal descuentoTotalLinea) { this.descuentoTotalLinea = descuentoTotalLinea; }
    public BigDecimal getDescuentoSinCobertura() { return descuentoSinCobertura; }
    public void setDescuentoSinCobertura(BigDecimal descuentoSinCobertura) { this.descuentoSinCobertura = descuentoSinCobertura; }
    public BigDecimal getCantidadSinCobertura() { return cantidadSinCobertura; }
    public void setCantidadSinCobertura(BigDecimal cantidadSinCobertura) { this.cantidadSinCobertura = cantidadSinCobertura; }
    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }
}