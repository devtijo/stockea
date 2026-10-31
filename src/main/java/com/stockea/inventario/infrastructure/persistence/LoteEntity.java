package com.stockea.inventario.infrastructure.persistence;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "lote")
public class LoteEntity {

    @Id
    private UUID id;

    @Column(name = "negocio_id", nullable = false)
    private UUID negocioId;

    @Column(name = "producto_id", nullable = false)
    private UUID productoId;

    @Column(name = "fecha_vencimiento", nullable = false)
    private LocalDate fechaVencimiento;

    @Column(name = "cantidad_inicial", nullable = false)
    private BigDecimal cantidadInicial;

    @Column(name = "cantidad_disponible", nullable = false)
    private BigDecimal cantidadDisponible;

    @Column(name = "costo_unitario", nullable = false)
    private BigDecimal costoUnitario;

    @Column(name = "estado", nullable = false)
    private String estado;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getNegocioId() { return negocioId; }
    public void setNegocioId(UUID negocioId) { this.negocioId = negocioId; }
    public UUID getProductoId() { return productoId; }
    public void setProductoId(UUID productoId) { this.productoId = productoId; }
    public LocalDate getFechaVencimiento() { return fechaVencimiento; }
    public void setFechaVencimiento(LocalDate fechaVencimiento) { this.fechaVencimiento = fechaVencimiento; }
    public BigDecimal getCantidadInicial() { return cantidadInicial; }
    public void setCantidadInicial(BigDecimal cantidadInicial) { this.cantidadInicial = cantidadInicial; }
    public BigDecimal getCantidadDisponible() { return cantidadDisponible; }
    public void setCantidadDisponible(BigDecimal cantidadDisponible) { this.cantidadDisponible = cantidadDisponible; }
    public BigDecimal getCostoUnitario() { return costoUnitario; }
    public void setCostoUnitario(BigDecimal costoUnitario) { this.costoUnitario = costoUnitario; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
}