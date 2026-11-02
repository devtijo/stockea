package com.stockea.sync.infrastructure.web;

import com.stockea.sync.application.DiscrepanciasService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/discrepancias")
@Validated
class DiscrepanciasController {

    private final DiscrepanciasService servicio;

    DiscrepanciasController(DiscrepanciasService servicio) {
        this.servicio = servicio;
    }

    record ResolverRequest(@NotBlank @Size(max = 300) String nota) {}

    @GetMapping
    List<DiscrepanciasService.Vista> listar(
            @RequestParam(defaultValue = "ABIERTA") @Pattern(regexp = "ABIERTA|RESUELTA") String estado) {
        return servicio.listar(estado);
    }

    @PostMapping("/{id}/resolver")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void resolver(@PathVariable UUID id, @Valid @RequestBody ResolverRequest req) {
        servicio.resolver(id, req.nota().strip());
    }
}
