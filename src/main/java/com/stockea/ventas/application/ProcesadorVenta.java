package com.stockea.ventas.application;

import com.stockea.sync.api.ComandoOperacion;
import com.stockea.sync.api.EstadoOperacion;
import com.stockea.sync.api.ProcesadorOperacion;
import com.stockea.sync.api.ResultadoOperacion;
import com.stockea.sync.api.TipoOperacion;
import com.stockea.ventas.domain.MetodoPago;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class ProcesadorVenta implements ProcesadorOperacion {

    private final VentasService ventasService;
    private final JsonMapper jsonMapper;

    public ProcesadorVenta(VentasService ventasService, JsonMapper jsonMapper) {
        this.ventasService = ventasService;
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

        return new ResultadoOperacion(cmd.operacionId(), EstadoOperacion.ACEPTADA, ventaId);
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