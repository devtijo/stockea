package com.stockea.ventas.application;

import com.stockea.sync.api.ComandoOperacion;
import com.stockea.sync.api.EstadoOperacion;
import com.stockea.sync.api.ProcesadorOperacion;
import com.stockea.sync.api.ResultadoOperacion;
import com.stockea.sync.api.TipoOperacion;
import com.stockea.ventas.domain.MetodoPago;
import com.stockea.ventas.infrastructure.persistence.ItemVentaRepository;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class ProcesadorVenta implements ProcesadorOperacion {

    private final VentasService ventasService;
    private final ItemVentaRepository itemVentaRepo;
    private final JsonMapper jsonMapper;

    public ProcesadorVenta(VentasService ventasService,
                           ItemVentaRepository itemVentaRepo,
                           JsonMapper jsonMapper) {
        this.ventasService = ventasService;
        this.itemVentaRepo = itemVentaRepo;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public TipoOperacion tipo() {
        return TipoOperacion.VENTA;
    }

    @Override
    public ResultadoOperacion procesar(ComandoOperacion cmd) {
        VentaPayload payload;
        try {
            payload = jsonMapper.readValue(cmd.payloadJson(), VentaPayload.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Payload de venta inválido", e);
        }

        var items = payload.items().stream()
                .map(i -> new VentasService.ItemCmd(i.productoId(), i.cantidad(), i.precioUnitario()))
                .toList();

        var metodo = MetodoPago.valueOf(payload.metodoPago());

        var ventaId = ventasService.registrarVenta(
                cmd.negocioId(), cmd.dispositivoId(), cmd.cajeroId(), cmd.turnoCajaId(),
                cmd.operacionId(), cmd.fechaCliente(), items,
                metodo, payload.montoRecibido(), payload.totalCliente()
        );

        var itemConFaltante = itemVentaRepo.findAll().stream()
                .filter(i -> i.getVentaId().equals(ventaId))
                .filter(i -> i.getCantidadSinCobertura() != null && i.getCantidadSinCobertura().signum() > 0)
                .findFirst();

        if (itemConFaltante.isPresent()) {
            var item = itemConFaltante.get();
            var info = new ResultadoOperacion.DiscrepanciaInfo(
                    item.getProductoId(),
                    null, // loteId: el faltante no tiene lote asignado
                    item.getCantidadSinCobertura(),
                    item.getPrecioUnitario(),
                    item.getPrecioUnitario()
            );
            return new ResultadoOperacion(cmd.operacionId(), EstadoOperacion.DISCREPANCIA, ventaId, info);
        }

        return new ResultadoOperacion(cmd.operacionId(), EstadoOperacion.ACEPTADA, ventaId, null);
    }

    public record VentaPayload(
            String metodoPago,
            BigDecimal montoRecibido,
            BigDecimal totalCliente,
            List<ItemPayload> items
    ) {
        public record ItemPayload(
                UUID productoId,
                BigDecimal cantidad,
                BigDecimal precioUnitario
        ) {}
    }
}