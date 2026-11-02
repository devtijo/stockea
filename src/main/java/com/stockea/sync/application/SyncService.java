package com.stockea.sync.application;

import com.stockea.sync.api.ComandoOperacion;
import com.stockea.sync.api.ProcesadorOperacion;
import com.stockea.sync.api.ResultadoOperacion;
import com.stockea.sync.api.TipoOperacion;
import com.stockea.sync.infrastructure.persistence.DiscrepanciaEntity;
import com.stockea.sync.infrastructure.persistence.DiscrepanciaRepository;
import com.stockea.sync.infrastructure.persistence.OperacionSyncEntity;
import com.stockea.sync.infrastructure.persistence.OperacionSyncId;
import com.stockea.sync.infrastructure.persistence.OperacionSyncRepository;
import com.stockea.sync.infrastructure.web.dto.SyncRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class SyncService {

    private final JdbcTemplate jdbcTemplate;
    private final OperacionSyncRepository operacionRepo;
    private final DiscrepanciaRepository discrepanciaRepo;
    private final List<ProcesadorOperacion> procesadores;
    private final Clock clock;

    public SyncService(JdbcTemplate jdbcTemplate,
                       OperacionSyncRepository operacionRepo,
                       DiscrepanciaRepository discrepanciaRepo,
                       List<ProcesadorOperacion> procesadores,
                       Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.operacionRepo = operacionRepo;
        this.discrepanciaRepo = discrepanciaRepo;
        this.procesadores = procesadores;
        this.clock = clock;
    }

    @Transactional
    public List<ResultadoOperacion> procesarOperaciones(
            UUID negocioId, UUID dispositivoId, UUID cajeroId, UUID turnoCajaId,
            SyncRequest request) {

        var resultados = new ArrayList<ResultadoOperacion>();

        for (var op : request.operaciones()) {
            // 1. Advisory lock (idempotencia a nivel de BD)
            jdbcTemplate.query(
                    "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",
                    rs -> {},
                    negocioId + ":" + op.operacionId()
            );

            // Verificar si la operación ya existe (idempotencia)
            var id = new OperacionSyncId(negocioId, op.operacionId());
            var existenteOpt = operacionRepo.findById(id);
            if (existenteOpt.isPresent()) {
                var e = existenteOpt.get();
                resultados.add(new ResultadoOperacion(e.getOperacionId(), e.getEstado(), e.getResultadoId()
                ));
                continue;
            }

            // Buscar el procesador según el tipo
            var procesador = procesadores.stream()
                    .filter(p -> p.tipo() == TipoOperacion.valueOf(op.tipo()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("No hay procesador para " + op.tipo()));

            // Procesar la operación
            var comando = new ComandoOperacion(
                    negocioId, dispositivoId, cajeroId, turnoCajaId,
                    op.operacionId(), op.fechaCliente(), op.payloadJson()
            );
            var resultado = procesador.procesar(comando);

            // Si hubo discrepancias, guardar una por cada item con faltante
            for (var info : resultado.discrepancias()) {
                var discrepancia = new DiscrepanciaEntity();
                discrepancia.setId(UUID.randomUUID());
                discrepancia.setNegocioId(negocioId);
                discrepancia.setOperacionId(op.operacionId());
                discrepancia.setVentaId(resultado.resultadoId());
                discrepancia.setItemVentaId(info.itemVentaId());
                discrepancia.setProductoId(info.productoId());
                discrepancia.setLoteId(info.loteId());
                discrepancia.setMotivo("STOCK_INSUFICIENTE");
                discrepancia.setCantidadFaltante(info.cantidadFaltante());
                discrepancia.setPrecioCobrado(info.precioCobrado());
                discrepancia.setPrecioEsperado(info.precioEsperado());
                discrepancia.setEstado("ABIERTA");
                discrepancia.setDetectadaEn(clock.instant());
                discrepanciaRepo.save(discrepancia);
            }

            // Guardar la operación de sincronización
            var entidad = new OperacionSyncEntity();
            entidad.setNegocioId(negocioId);
            entidad.setOperacionId(op.operacionId());
            entidad.setDispositivoId(dispositivoId);
            entidad.setUsuarioId(cajeroId);
            entidad.setTipo(TipoOperacion.valueOf(op.tipo()));
            entidad.setSecuenciaDispositivo(op.secuenciaDispositivo());
            entidad.setEstado(resultado.estado());
            entidad.setCreadaEnCliente(op.fechaCliente());
            entidad.setRecibidaEn(clock.instant());
            entidad.setProcesadaEn(clock.instant());
            entidad.setResultadoId(resultado.resultadoId());
            operacionRepo.save(entidad);

            resultados.add(resultado);
        }
        return resultados;
    }
}