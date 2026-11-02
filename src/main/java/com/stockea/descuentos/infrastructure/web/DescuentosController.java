package com.stockea.descuentos.infrastructure.web;

import com.stockea.descuentos.application.DescuentosService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/descuentos")
class DescuentosController {

    private final DescuentosService servicio;

    DescuentosController(DescuentosService servicio) {
        this.servicio = servicio;
    }

    record NuevoDescuentoRequest(@NotNull UUID loteId,
                                 @NotNull @DecimalMin("0.01") @DecimalMax("100") BigDecimal porcentaje) {
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, UUID> crear(@Valid @RequestBody NuevoDescuentoRequest req) {
        return Map.of("id", servicio.crear(new DescuentosService.NuevoDescuento(req.loteId(), req.porcentaje())));
    }

    @PostMapping("/{id}/desactivar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void desactivar(@PathVariable UUID id) {
        servicio.desactivar(id);
    }
}
