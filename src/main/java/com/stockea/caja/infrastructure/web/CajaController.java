package com.stockea.caja.infrastructure.web;

import com.stockea.caja.application.TurnosService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Abrir y cerrar caja NO pasa por aquí: viaja por /api/sync como cualquier operación offline
@RestController
@RequestMapping("/api/caja")
class CajaController {

    private final TurnosService turnos;

    CajaController(TurnosService turnos) {
        this.turnos = turnos;
    }

    @GetMapping("/turnos")
    List<TurnosService.Turno> turnos() {
        return turnos.ultimos();
    }
}
