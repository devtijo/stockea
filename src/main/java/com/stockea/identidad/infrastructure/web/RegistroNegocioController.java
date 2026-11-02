package com.stockea.identidad.infrastructure.web;

import com.stockea.identidad.application.RegistroNegocioService;
import com.stockea.identidad.infrastructure.web.IdentidadDtos.RegistroNegocioRequest;
import com.stockea.identidad.infrastructure.web.IdentidadDtos.RegistroNegocioResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// endpoint público (no hay token todavía)
@RestController
class RegistroNegocioController {

    private final RegistroNegocioService servicio;

    RegistroNegocioController(RegistroNegocioService servicio) {
        this.servicio = servicio;
    }

    @PostMapping("/api/publico/negocios")
    @ResponseStatus(HttpStatus.CREATED)
    RegistroNegocioResponse registrar(@Valid @RequestBody RegistroNegocioRequest r) {
        var admin = r.administrador();
        var registro = servicio.registrar(new RegistroNegocioService.Solicitud(
                r.negocio().nombre(), r.negocio().tipo(), r.negocio().ciudad(), r.negocio().direccion(),
                admin.documento().aDominio(), admin.nombre(), admin.correo(), admin.clave(), admin.pin(),
                r.aceptaPolitica(), r.versionPolitica()));
        return new RegistroNegocioResponse(registro.negocioId(), registro.usuarioId(), registro.nombreDeUsuario());
    }
}
