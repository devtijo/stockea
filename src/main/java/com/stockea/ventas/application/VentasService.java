package com.stockea.ventas.application;

import com.stockea.inventario.api.InventarioApi;
import com.stockea.shared.tiempo.FechaNegocio;
import com.stockea.ventas.domain.*;
import com.stockea.ventas.infrastructure.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class VentasService {

    private final InventarioApi inventarioApi;
    private final VentaRepository ventaRepo;
    private final ItemVentaRepository itemRepo;
    private final AsignacionLoteRepository asignacionRepo;
    private final Clock clock;
    private final ZoneId zonaNegocio = ZoneId.of("America/Bogota");

    public VentasService(InventarioApi inventarioApi, VentaRepository ventaRepo,
                         ItemVentaRepository itemRepo, AsignacionLoteRepository asignacionRepo,
                         Clock clock) {
        this.inventarioApi = inventarioApi;
        this.ventaRepo = ventaRepo;
        this.itemRepo = itemRepo;
        this.asignacionRepo = asignacionRepo;
        this.clock = clock;
    }

    @Transactional
    public UUID registrarVenta(UUID negocioId, UUID dispositivoId, UUID cajeroId, UUID turnoCajaId,
                               UUID operacionId, Instant fechaCliente,
                               List<ItemCmd> items, MetodoPago metodoPago,
                               BigDecimal montoRecibido, BigDecimal totalCliente) {
        var fechaVenta = FechaNegocio.calcular(fechaCliente, clock, zonaNegocio);

        // Armar items con FEFO
        List<ItemVenta> itemsProcesados = new ArrayList<>();
        for (var itemCmd : items) {
            var asignacion = inventarioApi.asignarFefo(
                    negocioId, itemCmd.productoId(), itemCmd.cantidad(), fechaVenta);

            var asignaciones = asignacion.fracciones().stream()
                    .map(f -> new AsignacionLote(
                            UUID.randomUUID(), f.loteId(), f.cantidad(),
                            null, BigDecimal.ZERO, OrigenAsignacion.DISPOSITIVO))
                    .toList();

            itemsProcesados.add(new ItemVenta(
                    UUID.randomUUID(), itemCmd.productoId(), itemCmd.cantidad(), itemCmd.precioUnitario(),
                    BigDecimal.ZERO, BigDecimal.ZERO, asignacion.sinCobertura(), asignaciones));
        }

        // Armar el cmd y la venta
        var cmd = new RegistrarVentaCmd(
                negocioId, operacionId, dispositivoId, cajeroId, turnoCajaId,
                metodoPago, totalCliente, BigDecimal.ZERO, totalCliente,
                montoRecibido, fechaCliente, List.of());

        Venta venta = Venta.registrar(cmd, itemsProcesados, clock.instant());

        // Persistir VentaEntity
        var ventaEntity = new VentaEntity();
        ventaEntity.setId(venta.getId());
        ventaEntity.setNegocioId(negocioId);
        ventaEntity.setOperacionId(operacionId);
        ventaEntity.setDispositivoId(dispositivoId);
        ventaEntity.setCajeroId(cajeroId);
        ventaEntity.setTurnoCajaId(turnoCajaId);
        ventaEntity.setEstado(venta.getEstado().name());
        ventaEntity.setMetodoPago(venta.getMetodoPago().name());
        ventaEntity.setSubtotal(venta.getSubtotal());
        ventaEntity.setDescuentoTotal(venta.getDescuentoTotal());
        ventaEntity.setTotal(venta.getTotal());
        ventaEntity.setMontoRecibido(venta.getMontoRecibido());
        ventaEntity.setCambio(venta.getCambio());
        ventaEntity.setFechaCliente(venta.getFechaCliente());
        ventaEntity.setRegistradaEn(clock.instant());
        ventaRepo.save(ventaEntity);

        // Persistir items y asignaciones
        short numeroLinea = 1;
        for (ItemVenta item : venta.getItems()) {
            var itemEntity = new ItemVentaEntity();
            itemEntity.setId(item.id());
            itemEntity.setNegocioId(negocioId);
            itemEntity.setVentaId(venta.getId());
            itemEntity.setProductoId(item.productoId());
            itemEntity.setLinea(numeroLinea++);
            itemEntity.setCantidad(item.cantidad());
            itemEntity.setPrecioUnitario(item.precioUnitario());
            itemEntity.setDescuentoTotalLinea(item.descuentoTotalLinea());
            itemEntity.setDescuentoSinCobertura(item.descuentoSinCobertura());
            itemEntity.setCantidadSinCobertura(item.cantidadSinCobertura());
            itemEntity.setSubtotal(item.calcularSubtotalLinea());
            itemRepo.save(itemEntity);

            for (AsignacionLote asig : item.asignaciones()) {
                var asigEntity = new AsignacionLoteEntity();
                asigEntity.setId(asig.id());
                asigEntity.setNegocioId(negocioId);
                asigEntity.setItemVentaId(item.id());
                asigEntity.setLoteId(asig.loteId());
                asigEntity.setCantidad(asig.cantidad());
                asigEntity.setDescuentoId(asig.descuentoId());
                asigEntity.setMontoDescuento(asig.montoDescuento());
                asigEntity.setOrigen(asig.origen().name());
                asignacionRepo.save(asigEntity);
            }
        }

        return venta.getId();
    }

    public record ItemCmd(UUID productoId, BigDecimal cantidad, BigDecimal precioUnitario) {}
}