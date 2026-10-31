package com.stockea.sync.infrastructure.web;

import com.stockea.shared.dev.TenantDev;
import com.stockea.sync.application.SyncService;
import com.stockea.sync.infrastructure.web.dto.SyncRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/sync")
public class SyncController {

    private final SyncService syncService;

    public SyncController(SyncService syncService) {
        this.syncService = syncService;
    }

    @PostMapping("/operaciones")
    public ResponseEntity<Map<String, Object>> recibir(@RequestBody SyncRequest request) {
        var resultados = syncService.procesarOperaciones(
                TenantDev.NEGOCIO_ID(),
                TenantDev.DISPOSITIVO_ID(),
                TenantDev.CAJERO_ID(),
                TenantDev.TURNO_ID(),
                request
        );
        return ResponseEntity.ok(Map.of("resultados", resultados));
    }
}