package com.stockea.identidad.infrastructure.web;

import com.stockea.identidad.application.PerfilService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class PerfilController {

    private final PerfilService servicio;

    PerfilController(PerfilService servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/api/yo")
    PerfilService.Perfil yo() {
        return servicio.actual();
    }
}
