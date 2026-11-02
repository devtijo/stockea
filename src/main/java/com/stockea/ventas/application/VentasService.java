package com.stockea.ventas.application;

import com.stockea.descuentos.api.DescuentosApi;
import com.stockea.inventario.api.FraccionLote;
import com.stockea.inventario.api.InventarioApi;
import com.stockea.shared.tiempo.FechaNegocio;
import com.stockea.ventas.domain.*;
import com.stockea.ventas.infrastructure.persistence.*;
import com.stockea.shared.metricas.DespuesDelCommit;
import com.stockea.ventas.api.VentaRegistrada;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.ApplicationEventPublisher;
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
    private final DescuentosApi descuentosApi;
    private final VentaRepository ventaRepo;
    private final ItemVentaRepository itemRepo;
    private final AsignacionLoteRepository asignacionRepo;
    private final Clock clock;
    private final ApplicationEventPublisher eventos;
    private final MeterRegistry metricas;
    private final ZoneId zonaNegocio = ZoneId.of("America/Bogota");

    public VentasService(InventarioApi inventarioApi, DescuentosApi descuentosApi, VentaRepository ventaRepo,
                         ItemVentaRepository itemRepo, AsignacionLoteRepository asignacionRepo,
                         Clock clock, ApplicationEventPublisher eventos, MeterRegistry metricas) {
        this.inventarioApi = inventarioApi;
        this.descuentosApi = descuentosApi;
        this.ventaRepo = ventaRepo;
        this.itemRepo = itemRepo;
        this.asignacionRepo = asignacionRepo;
        this.clock = clock;
        this.eventos = eventos;
        this.metricas = metricas;
        for (var metodo : MetodoPago.values()) {
            metricas.counter("stockea.ventas.pesos", "metodo_pago", metodo.name());
            for (var estado : EstadoVenta.values()) {
                metricas.counter("stockea.ventas", "metodo_pago", metodo.name(), "estado", estado.name());
            }
        }
    }

    // Diferencia entre lo que el dispositivo cobró y lo que debió cobrar en una línea
    public record DiferenciaPrecio(UUID itemVentaId, UUID productoId, BigDecimal cobrado, BigDecimal esperado) {}

    public record Resultado(UUID ventaId, List<DiferenciaPrecio> diferencias) {}

    // Tolerancia de redondeo: el peso colombiano no usa centavos
    private static final BigDecimal TOLERANCIA = BigDecimal.ONE;

    @Transactional
    public Resultado registrarVenta(UUID negocioId, UUID dispositivoId, UUID cajeroId, UUID usuarioSyncId,
                                    UUID turnoCajaId, UUID operacionId, Instant fechaCliente,
                                    List<ItemCmd> items, MetodoPago metodoPago,
                                    BigDecimal montoRecibido, BigDecimal totalCliente) {
        var fechaVenta = FechaNegocio.calcular(fechaCliente, clock, zonaNegocio);

        // Armar items con FEFO
        List<ItemVenta> itemsProcesados = new ArrayList<>();
        List<DiferenciaPrecio> diferencias = new ArrayList<>();
        List<FraccionLote> salidas = new ArrayList<>();
        for (var itemCmd : items) {
            var asignacion = inventarioApi.asignarFefo(
                    negocioId, itemCmd.productoId(), itemCmd.cantidad(), fechaVenta);
            salidas.addAll(asignacion.fracciones());

            // Precio que regía cuando se hizo la venta
            var precioEsperado = inventarioApi.precioVigente(negocioId, itemCmd.productoId(), fechaCliente)
                    .orElse(itemCmd.precioUnitario());

            // Descuento REAL: el de los lotes que FEFO entregó, no el que el dispositivo supuso
            var descuentosLote = descuentosApi.vigentesPorLote(negocioId,
                    asignacion.fracciones().stream().map(FraccionLote::loteId).toList());
            BigDecimal descuentoReal = BigDecimal.ZERO;
            List<AsignacionLote> asignaciones = new ArrayList<>();
            for (var f : asignacion.fracciones()) {
                var d = descuentosLote.get(f.loteId());
                var monto = d == null ? BigDecimal.ZERO
                        : descuentosApi.calcularMonto(f.cantidad(), precioEsperado, d.porcentaje());
                descuentoReal = descuentoReal.add(monto);
                asignaciones.add(new AsignacionLote(UUID.randomUUID(), f.loteId(), f.cantidad(),
                        d == null ? null : d.id(), monto, OrigenAsignacion.DISPOSITIVO));
            }

            // El servidor NO recalcula lo cobrado guarda lo que cobró el dispositivo
            var descuentoCliente = itemCmd.descuento() == null ? BigDecimal.ZERO : itemCmd.descuento();
            var sinCobertura = descuentoCliente.subtract(descuentoReal).max(BigDecimal.ZERO);

            var itemId = UUID.randomUUID();
            itemsProcesados.add(new ItemVenta(
                    itemId, itemCmd.productoId(), itemCmd.cantidad(), itemCmd.precioUnitario(),
                    descuentoCliente, sinCobertura, asignacion.sinCobertura(), asignaciones));

            var cobrado = itemCmd.cantidad().multiply(itemCmd.precioUnitario()).subtract(descuentoCliente);
            var esperado = itemCmd.cantidad().multiply(precioEsperado).subtract(descuentoReal);
            if (cobrado.subtract(esperado).abs().compareTo(TOLERANCIA) >= 0) {
                diferencias.add(new DiferenciaPrecio(itemId, itemCmd.productoId(), cobrado, esperado));
            }
        }

        // Armar el cmd y la venta
        var cmd = new RegistrarVentaCmd(
                negocioId, operacionId, dispositivoId, cajeroId, turnoCajaId,
                metodoPago, totalCliente, BigDecimal.ZERO, totalCliente,
                montoRecibido, fechaCliente, List.of());

        Venta venta = Venta.registrar(cmd, itemsProcesados, clock.instant(), !diferencias.isEmpty());

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

        // Kardex: un movimiento VENTA por cada lote del que salió mercancía
        inventarioApi.registrarSalidaPorVenta(negocioId, salidas, operacionId, usuarioSyncId);

        // Dentro de la transacción: Modulith lo guarda en el outbox y lo envía a RabbitMQ tras el COMMIT.
        eventos.publishEvent(new VentaRegistrada(
                negocioId, venta.getId(), operacionId, venta.getEstado().name(), fechaVenta,
                venta.getTotal(), venta.getMetodoPago().name(), ventaEntity.getRegistradaEn(),
                venta.getItems().stream()
                        .map(i -> new VentaRegistrada.Item(i.productoId(), i.cantidad()))
                        .toList()));

        registrarMetricas(venta);
        return new Resultado(venta.getId(), diferencias);
    }

    // Métricas de negocio: ventas y pesos vendidos, solo tras el COMMIT
    private void registrarMetricas(Venta venta) {
        var metodo = venta.getMetodoPago().name();
        var estado = venta.getEstado().name();
        var total = venta.getTotal().doubleValue();
        DespuesDelCommit.ejecutar(() -> {
            metricas.counter("stockea.ventas", "metodo_pago", metodo, "estado", estado).increment();
            metricas.counter("stockea.ventas.pesos", "metodo_pago", metodo).increment(total);
        });
    }

    public record ItemCmd(UUID productoId, BigDecimal cantidad, BigDecimal precioUnitario, BigDecimal descuento) {}
}