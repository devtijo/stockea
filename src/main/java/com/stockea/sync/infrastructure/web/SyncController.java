package com.stockea.sync.infrastructure.web;

import com.stockea.identidad.api.DispositivoApi;
import com.stockea.shared.dev.TenantDev;
import com.stockea.shared.tenant.TenantContext;
import com.stockea.sync.application.SyncService;
import com.stockea.sync.infrastructure.web.dto.SyncRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/sync")
public class SyncController {

    private final SyncService syncService;
    private final DispositivoApi dispositivos;

    public SyncController(SyncService syncService, DispositivoApi dispositivos) {
        this.syncService = syncService;
        this.dispositivos = dispositivos;
    }

    @PostMapping("/operaciones")
    public ResponseEntity<Map<String, Object>> recibir(@RequestHeader("X-Dispositivo-Id") UUID dispositivoId,
                                                       @Valid @RequestBody SyncRequest request) {
        var tenant = TenantContext.requerido();
        dispositivos.registrarUso(tenant.negocioId(), dispositivoId, tenant.usuarioId());
        var resultados = syncService.procesarOperaciones(
                tenant.negocioId(),
                dispositivoId,
                tenant.usuarioId(),
                TenantDev.TURNO_ID(),
                request
        );
        return ResponseEntity.ok(Map.of("resultados", resultados));
    }
}
