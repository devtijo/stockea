package com.stockea.identidad.infrastructure.web;

import com.stockea.identidad.application.UsuariosService;
import com.stockea.identidad.application.UsuariosService.UsuarioResumen;
import com.stockea.identidad.infrastructure.web.IdentidadDtos.NuevoUsuarioRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

// Gestión de usuarios del negocio
@RestController
@RequestMapping("/api/usuarios")
class UsuariosController {

    private final UsuariosService servicio;

    UsuariosController(UsuariosService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    List<UsuarioResumen> listar() {
        return servicio.listar();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    UsuarioResumen crear(@Valid @RequestBody NuevoUsuarioRequest r) {
        return servicio.crear(new UsuariosService.NuevoUsuario(r.documento().aDominio(), r.nombre(), r.correo(),
                r.rol(), r.claveTemporal(), r.pin()));
    }

    // No hay DELETE: inhabilitar conserva el historial
    @PostMapping("/{id}/inhabilitar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void inhabilitar(@PathVariable UUID id) {
        servicio.inhabilitar(id);
    }
}
