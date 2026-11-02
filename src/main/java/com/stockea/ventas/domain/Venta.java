package com.stockea.ventas.domain;

import com.stockea.shared.domain.DomainException;
import com.stockea.shared.domain.ErrorDominio;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class Venta {
    private final UUID id;
    private final UUID negocioId;
    private final UUID operacionId;
    private final UUID dispositivoId;
    private final UUID cajeroId;
    private final UUID turnoCajaId;
    private final EstadoVenta estado;
    private final MetodoPago metodoPago;
    private final BigDecimal subtotal;
    private final BigDecimal descuentoTotal;
    private final BigDecimal total;
    private final BigDecimal montoRecibido;
    private final BigDecimal cambio;
    private final Instant fechaCliente;
    private final Instant registradaEn;
    private final List<ItemVenta> items;

    private Venta(UUID id, UUID negocioId, UUID operacionId, UUID dispositivoId, UUID cajeroId,
                  UUID turnoCajaId, EstadoVenta estado, MetodoPago metodoPago,
                  BigDecimal subtotal, BigDecimal descuentoTotal, BigDecimal total,
                  BigDecimal montoRecibido, BigDecimal cambio,
                  Instant fechaCliente, Instant registradaEn, List<ItemVenta> items) {
        this.id = id;
        this.negocioId = negocioId;
        this.operacionId = operacionId;
        this.dispositivoId = dispositivoId;
        this.cajeroId = cajeroId;
        this.turnoCajaId = turnoCajaId;
        this.estado = estado;
        this.metodoPago = metodoPago;
        this.subtotal = subtotal;
        this.descuentoTotal = descuentoTotal;
        this.total = total;
        this.montoRecibido = montoRecibido;
        this.cambio = cambio;
        this.fechaCliente = fechaCliente;
        this.registradaEn = registradaEn;
        this.items = List.copyOf(items);
    }

    public static Venta registrar(RegistrarVentaCmd cmd, List<ItemVenta> itemsProcesados, Instant horaServidor) {
        return registrar(cmd, itemsProcesados, horaServidor, false);
    }

    // hayDiferenciaDePrecio: el precio o el descuento cobrado no coincide con el vigente
    // La venta se acepta igual, pero queda en DISCREPANCIA para que el supervisor la revise.
    public static Venta registrar(RegistrarVentaCmd cmd, List<ItemVenta> itemsProcesados, Instant horaServidor,
                                  boolean hayDiferenciaDePrecio) {
        if (cmd == null) throw new DomainException(ErrorDominio.COMANDO_VENTA_OBLIGATORIO);
        if (itemsProcesados == null) throw new DomainException(ErrorDominio.ITEMS_PROCESADOS_OBLIGATORIOS);
        if (horaServidor == null) throw new DomainException(ErrorDominio.HORA_SERVIDOR_OBLIGATORIA);
        if (cmd.totalCliente() == null) throw new DomainException(ErrorDominio.TOTAL_CLIENTE_OBLIGATORIO);

        // Subtotal bruto = Σ(cantidad × precioUnitario)
        BigDecimal subtotalBruto = itemsProcesados.stream()
                .map(i -> i.cantidad().multiply(i.precioUnitario()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Descuento total = Σ(descuentoTotalLinea)
        BigDecimal descuentoCalculado = itemsProcesados.stream()
                .map(ItemVenta::descuentoTotalLinea)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Total = subtotal − descuento
        BigDecimal totalCalculado = subtotalBruto.subtract(descuentoCalculado);

        boolean hayDiscrepanciaStock = itemsProcesados.stream().anyMatch(ItemVenta::tieneDiscrepancia);
        boolean hayDiscrepanciaTotal = totalCalculado.compareTo(cmd.totalCliente()) != 0;
        EstadoVenta nuevoEstado = (hayDiscrepanciaStock || hayDiscrepanciaTotal || hayDiferenciaDePrecio)
                ? EstadoVenta.DISCREPANCIA : EstadoVenta.ACEPTADA;

        BigDecimal cambioCalculado = BigDecimal.ZERO;
        if (cmd.metodoPago() == MetodoPago.EFECTIVO) {
            if (cmd.montoRecibido() == null) {
                throw new DomainException(ErrorDominio.MONTO_RECIBIDO_OBLIGATORIO);
            }
            if (cmd.montoRecibido().compareTo(totalCalculado) < 0) {
                throw new DomainException(ErrorDominio.MONTO_RECIBIDO_INSUFICIENTE);
            }
            cambioCalculado = cmd.montoRecibido().subtract(totalCalculado);
        }

        return new Venta(
                UUID.randomUUID(), cmd.negocioId(), cmd.operacionId(),
                cmd.dispositivoId(), cmd.cajeroId(), cmd.turnoCajaId(),
                nuevoEstado, cmd.metodoPago(),
                subtotalBruto, descuentoCalculado, totalCalculado,
                cmd.montoRecibido(), cambioCalculado,
                cmd.fechaCliente(), horaServidor, itemsProcesados
        );
    }

    // Getters
    public UUID getId() { return id; }
    public UUID getNegocioId() { return negocioId; }
    public UUID getOperacionId() { return operacionId; }
    public UUID getDispositivoId() { return dispositivoId; }
    public UUID getCajeroId() { return cajeroId; }
    public UUID getTurnoCajaId() { return turnoCajaId; }
    public EstadoVenta getEstado() { return estado; }
    public MetodoPago getMetodoPago() { return metodoPago; }
    public BigDecimal getSubtotal() { return subtotal; }
    public BigDecimal getDescuentoTotal() { return descuentoTotal; }
    public BigDecimal getTotal() { return total; }
    public BigDecimal getMontoRecibido() { return montoRecibido; }
    public BigDecimal getCambio() { return cambio; }
    public Instant getFechaCliente() { return fechaCliente; }
    public Instant getRegistradaEn() { return registradaEn; }
    public List<ItemVenta> getItems() { return items; }
}