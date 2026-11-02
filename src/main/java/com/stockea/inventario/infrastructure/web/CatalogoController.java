package com.stockea.inventario.infrastructure.web;

import com.stockea.inventario.application.CatalogoService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
class CatalogoController {

    private final CatalogoService servicio;

    CatalogoController(CatalogoService servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/api/catalogo")
    List<CatalogoService.ProductoCatalogo> catalogo() {
        return servicio.catalogo();
    }
}
