package com.stockea.ventas.application;

import com.stockea.shared.domain.ErrorDominio;
import com.stockea.shared.domain.DomainException;
import com.stockea.identidad.api.NegocioApi;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ProcesadorVenta implements ProcesadorOperacion {

    private final VentasService ventasService;
    private final ItemVentaRepository itemVentaRepo;
    private final NegocioApi negocioApi;
    private final JsonMapper jsonMapper;

    public ProcesadorVenta(VentasService ventasService,
                           ItemVentaRepository itemVentaRepo,
                           NegocioApi negocioApi,
                           JsonMapper jsonMapper) {
        this.ventasService = ventasService;
        this.itemVentaRepo = itemVentaRepo;
        this.negocioApi = negocioApi;
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
            throw new DomainException(ErrorDominio.PAYLOAD_VENTA_INVALIDO);
        }

        // Toda venta pertenece a un turno de caja (ADR-025). Sin turno no hay cuadre posible.
        if (payload.turnoId() == null) {
            return new ResultadoOperacion(cmd.operacionId(), EstadoOperacion.RECHAZADA_SIN_TURNO, null);
        }

        // El cajero es quien estaba en la caja al vender, no quien sincroniza.
        var cajero = negocioApi.esUsuarioDelNegocio(cmd.negocioId(), payload.cajeroId())
                ? payload.cajeroId() : cmd.usuarioId();

        var items = payload.items().stream()
                .map(i -> new VentasService.ItemCmd(i.productoId(), i.cantidad(), i.precioUnitario(), i.descuento()))
                .toList();

        var metodo = MetodoPago.valueOf(payload.metodoPago());

        var resultado = ventasService.registrarVenta(
                cmd.negocioId(), cmd.dispositivoId(), cajero, cmd.usuarioId(), payload.turnoId(),
                cmd.operacionId(), cmd.fechaCliente(), items,
                metodo, payload.montoRecibido(), payload.totalCliente()
        );

        var discrepancias = new ArrayList<ResultadoOperacion.DiscrepanciaInfo>();

        // Una discrepancia por CADA item que no alcanzó a cubrirse con stock (ADR-002)
        itemVentaRepo.findByVentaIdOrderByLinea(resultado.ventaId()).stream()
                .filter(i -> i.getCantidadSinCobertura() != null && i.getCantidadSinCobertura().signum() > 0)
                .map(i -> new ResultadoOperacion.DiscrepanciaInfo(
                        "STOCK_INSUFICIENTE",
                        i.getId(),
                        i.getProductoId(),
                        null, // el faltante no tiene lote asignado
                        i.getCantidadSinCobertura(),
                        i.getPrecioUnitario(),
                        i.getPrecioUnitario()))
                .forEach(discrepancias::add);

        // Y una por cada línea cobrada distinto a lo vigente (ADR-021): se guarda el valor neto de la línea
        resultado.diferencias().stream()
                .map(d -> new ResultadoOperacion.DiscrepanciaInfo(
                        "PRECIO_DIFERENTE",
                        d.itemVentaId(),
                        d.productoId(),
                        null,
                        BigDecimal.ZERO,
                        d.cobrado(),
                        d.esperado()))
                .forEach(discrepancias::add);

        var estado = discrepancias.isEmpty() ? EstadoOperacion.ACEPTADA : EstadoOperacion.DISCREPANCIA;
        return new ResultadoOperacion(cmd.operacionId(), estado, resultado.ventaId(), discrepancias);
    }

    // turnoId y cajeroId los fija el dispositivo en el momento de la venta
    public record VentaPayload(
            UUID turnoId,
            UUID cajeroId,
            String metodoPago,
            BigDecimal montoRecibido,
            BigDecimal totalCliente,
            List<ItemPayload> items
    ) {
        public record ItemPayload(
                UUID productoId,
                BigDecimal cantidad,
                BigDecimal precioUnitario,
                BigDecimal descuento
        ) {}
    }
}
