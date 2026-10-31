package com.stockea.sync.application;

import com.stockea.sync.api.ComandoOperacion;
import com.stockea.sync.api.ProcesadorOperacion;
import com.stockea.sync.api.ResultadoOperacion;
import com.stockea.sync.api.TipoOperacion;         // <-- API, no DOMAIN
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
    private final List<ProcesadorOperacion> procesadores;
    private final Clock clock;

    public SyncService(JdbcTemplate jdbcTemplate, OperacionSyncRepository operacionRepo,
                       List<ProcesadorOperacion> procesadores, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.operacionRepo = operacionRepo;
        this.procesadores = procesadores;
        this.clock = clock;
    }

    @Transactional
    public List<ResultadoOperacion> procesarOperaciones(
            UUID negocioId, UUID dispositivoId, UUID cajeroId, UUID turnoCajaId,
            SyncRequest request) {

        var resultados = new ArrayList<ResultadoOperacion>();

        for (var op : request.operaciones()) {
            jdbcTemplate.query(
                    "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",
                    rs -> {},
                    negocioId + ":" + op.operacionId()
            );

            var id = new OperacionSyncId(negocioId, op.operacionId());
            var existenteOpt = operacionRepo.findById(id);
            if (existenteOpt.isPresent()) {
                var e = existenteOpt.get();
                resultados.add(new ResultadoOperacion(e.getOperacionId(), e.getEstado(), e.getResultadoId()));
                continue;
            }

            var procesador = procesadores.stream()
                    .filter(p -> p.tipo() == TipoOperacion.valueOf(op.tipo()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("No hay procesador para " + op.tipo()));

            var comando = new ComandoOperacion(
                    negocioId, dispositivoId, cajeroId, turnoCajaId,
                    op.operacionId(), op.fechaCliente(), op.payloadJson()
            );
            var resultado = procesador.procesar(comando);

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