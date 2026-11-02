package com.stockea.analitica.infrastructure.web;

import com.stockea.analitica.application.VelocidadVentaService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;

@RestController
@RequestMapping("/api/analitica")
@Validated
class VelocidadVentaController {

    private final VelocidadVentaService servicio;

    VelocidadVentaController(VelocidadVentaService servicio) {
        this.servicio = servicio;
    }

    // Velocidad de venta y días de cobertura por producto
    @GetMapping("/velocidad")
    List<VelocidadVentaService.VelocidadProducto> velocidad(
            @RequestParam(defaultValue = "7") @Min(1) @Max(90) int dias) {
        return servicio.consultar(dias);
    }
}
