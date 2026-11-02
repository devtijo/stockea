package com.stockea.inventario.infrastructure.web;

import com.stockea.inventario.application.InventarioService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/inventario")
class InventarioController {

    private final InventarioService servicio;

    InventarioController(InventarioService servicio) {
        this.servicio = servicio;
    }

    record ProductoRequest(@NotBlank @Size(max = 120) String nombre,
                           @Size(max = 60) String categoria,
                           @NotNull @Pattern(regexp = "UNIDAD|KILOGRAMO|LIBRA") String unidadVenta,
                           @NotNull @DecimalMin("1") BigDecimal precio) {
    }

    record PrecioRequest(@NotNull @DecimalMin("1") BigDecimal precio) {
    }

    record EntradaRequest(@NotNull UUID productoId,
                          @NotNull @DecimalMin("0.001") BigDecimal cantidad,
                          @NotNull LocalDate fechaVencimiento,
                          @NotNull @DecimalMin("0") BigDecimal costoUnitario) {
    }

    @GetMapping("/productos")
    List<InventarioService.ProductoResumen> productos() {
        return servicio.productos();
    }

    @PostMapping("/productos")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, UUID> crear(@Valid @RequestBody ProductoRequest r) {
        return Map.of("id", servicio.crearProducto(
                new InventarioService.NuevoProducto(r.nombre(), r.categoria(), r.unidadVenta(), r.precio())));
    }

    @PutMapping("/productos/{id}/precio")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void cambiarPrecio(@PathVariable UUID id, @Valid @RequestBody PrecioRequest r) {
        servicio.cambiarPrecio(id, r.precio());
    }

    @PostMapping("/productos/{id}/desactivar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void desactivar(@PathVariable UUID id) {
        servicio.cambiarEstado(id, false);
    }

    @PostMapping("/productos/{id}/activar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void activar(@PathVariable UUID id) {
        servicio.cambiarEstado(id, true);
    }

    @PostMapping("/entradas")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, UUID> entrada(@Valid @RequestBody EntradaRequest r) {
        return Map.of("loteId", servicio.registrarEntrada(new InventarioService.Entrada(
                r.productoId(), r.cantidad(), r.fechaVencimiento(), r.costoUnitario())));
    }
}
