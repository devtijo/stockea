package com.stockea.ventas.infrastructure.persistence;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "venta")
public class VentaEntity {

    @Id
    private UUID id;

    @Column(name = "negocio_id", nullable = false)
    private UUID negocioId;

    @Column(name = "operacion_id", nullable = false)
    private UUID operacionId;

    @Column(name = "dispositivo_id", nullable = false)
    private UUID dispositivoId;

    @Column(name = "cajero_id", nullable = false)
    private UUID cajeroId;

    @Column(name = "turno_caja_id", nullable = false)
    private UUID turnoCajaId;

    @Column(nullable = false, length = 20)
    private String estado;

    @Column(name = "metodo_pago", nullable = false, length = 20)
    private String metodoPago;

    @Column(nullable = false)
    private BigDecimal subtotal;

    @Column(name = "descuento_total", nullable = false)
    private BigDecimal descuentoTotal;

    @Column(nullable = false)
    private BigDecimal total;

    @Column(name = "monto_recibido", nullable = false)
    private BigDecimal montoRecibido;

    @Column(nullable = false)
    private BigDecimal cambio;

    @Column(name = "fecha_cliente", nullable = false)
    private Instant fechaCliente;

    @Column(name = "registrada_en", nullable = false)
    private Instant registradaEn;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getNegocioId() { return negocioId; }
    public void setNegocioId(UUID negocioId) { this.negocioId = negocioId; }
    public UUID getOperacionId() { return operacionId; }
    public void setOperacionId(UUID operacionId) { this.operacionId = operacionId; }
    public UUID getDispositivoId() { return dispositivoId; }
    public void setDispositivoId(UUID dispositivoId) { this.dispositivoId = dispositivoId; }
    public UUID getCajeroId() { return cajeroId; }
    public void setCajeroId(UUID cajeroId) { this.cajeroId = cajeroId; }
    public UUID getTurnoCajaId() { return turnoCajaId; }
    public void setTurnoCajaId(UUID turnoCajaId) { this.turnoCajaId = turnoCajaId; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public String getMetodoPago() { return metodoPago; }
    public void setMetodoPago(String metodoPago) { this.metodoPago = metodoPago; }
    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }
    public BigDecimal getDescuentoTotal() { return descuentoTotal; }
    public void setDescuentoTotal(BigDecimal descuentoTotal) { this.descuentoTotal = descuentoTotal; }
    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }
    public BigDecimal getMontoRecibido() { return montoRecibido; }
    public void setMontoRecibido(BigDecimal montoRecibido) { this.montoRecibido = montoRecibido; }
    public BigDecimal getCambio() { return cambio; }
    public void setCambio(BigDecimal cambio) { this.cambio = cambio; }
    public Instant getFechaCliente() { return fechaCliente; }
    public void setFechaCliente(Instant fechaCliente) { this.fechaCliente = fechaCliente; }
    public Instant getRegistradaEn() { return registradaEn; }
    public void setRegistradaEn(Instant registradaEn) { this.registradaEn = registradaEn; }
}