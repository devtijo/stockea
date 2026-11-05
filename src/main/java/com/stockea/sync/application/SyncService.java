package com.stockea.sync.application;

import com.stockea.shared.domain.ReglaNegocioException;
import com.stockea.sync.domain.ErrorSync;
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
import com.stockea.shared.metricas.DespuesDelCommit;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class SyncService {

    // Nombres de métricas y etiquetas en un solo lugar
    private static final String METRICA_OPERACIONES = "stockea.sync.operaciones";
    private static final String METRICA_DISCREPANCIAS = "stockea.discrepancias.registradas";
    private static final String TIPO = "tipo";
    private static final String ESTADO = "estado";
    private static final String MOTIVO = "motivo";

    private static final Logger log = LoggerFactory.getLogger(SyncService.class);

    private final JdbcTemplate jdbcTemplate;
    private final OperacionSyncRepository operacionRepo;
    private final DiscrepanciaRepository discrepanciaRepo;
    private final List<ProcesadorOperacion> procesadores;
    private final Clock clock;
    private final MeterRegistry metricas;

    public SyncService(JdbcTemplate jdbcTemplate,
                       OperacionSyncRepository operacionRepo,
                       DiscrepanciaRepository discrepanciaRepo,
                       List<ProcesadorOperacion> procesadores,
                       Clock clock,
                       MeterRegistry metricas) {
        this.jdbcTemplate = jdbcTemplate;
        this.operacionRepo = operacionRepo;
        this.discrepanciaRepo = discrepanciaRepo;
        this.procesadores = procesadores;
        this.clock = clock;
        this.metricas = metricas;
        // Contadores en 0 desde el arranque: increase() de Prometheus no cuenta el primer
        // incremento de una serie que aún no existía (las alertas y paneles lo perderían).
        for (var estado : new String[]{"ACEPTADA", "DISCREPANCIA"}) {
            metricas.counter(METRICA_OPERACIONES, TIPO, "VENTA", ESTADO, estado);
        }
        for (var tipo : new String[]{"ABRIR_TURNO", "CERRAR_TURNO"}) {
            metricas.counter(METRICA_OPERACIONES, TIPO, tipo, ESTADO, "ACEPTADA");
        }
        for (var motivo : new String[]{"STOCK_INSUFICIENTE", "PRECIO_DIFERENTE"}) {
            metricas.counter(METRICA_DISCREPANCIAS, MOTIVO, motivo);
        }
    }

    @Transactional
    public List<ResultadoOperacion> procesarOperaciones(
            UUID negocioId, UUID dispositivoId, UUID usuarioId, SyncRequest request) {

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
                    .orElseThrow(() -> new ReglaNegocioException(ErrorSync.TIPO_OPERACION_DESCONOCIDO, op.tipo()));

            // Procesar la operación
            var comando = new ComandoOperacion(
                    negocioId, dispositivoId, usuarioId,
                    op.operacionId(), op.fechaCliente(), op.payloadJson()
            );
            var resultado = procesador.procesar(comando);

            // Si hubo discrepancias, guardar una por cada item con faltante o con precio distinto
            for (var info : resultado.discrepancias()) {
                var discrepancia = new DiscrepanciaEntity();
                discrepancia.setId(UUID.randomUUID());
                discrepancia.setNegocioId(negocioId);
                discrepancia.setOperacionId(op.operacionId());
                discrepancia.setVentaId(resultado.resultadoId());
                discrepancia.setItemVentaId(info.itemVentaId());
                discrepancia.setProductoId(info.productoId());
                discrepancia.setLoteId(info.loteId());
                discrepancia.setMotivo(info.motivo());
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
            entidad.setUsuarioId(usuarioId);
            entidad.setTipo(TipoOperacion.valueOf(op.tipo()));
            entidad.setSecuenciaDispositivo(op.secuenciaDispositivo());
            entidad.setEstado(resultado.estado());
            entidad.setCreadaEnCliente(op.fechaCliente());
            entidad.setRecibidaEn(clock.instant());
            entidad.setProcesadaEn(clock.instant());
            entidad.setResultadoId(resultado.resultadoId());
            operacionRepo.save(entidad);

            registrarMetricasYLog(op.tipo(), op.operacionId(), resultado);
            resultados.add(resultado);
        }
        return resultados;
    }

    // Observabilidad: solo cuenta si la transacción hace COMMIT
    private void registrarMetricasYLog(String tipo, UUID operacionId, ResultadoOperacion resultado) {
        var estado = resultado.estado().name();
        var motivos = resultado.discrepancias().stream().map(ResultadoOperacion.DiscrepanciaInfo::motivo).toList();
        DespuesDelCommit.ejecutar(() -> {
            metricas.counter(METRICA_OPERACIONES, TIPO, tipo, ESTADO, estado).increment();
            for (var motivo : motivos) {
                metricas.counter(METRICA_DISCREPANCIAS, MOTIVO, motivo).increment();
            }
            MDC.put("operacion_id", operacionId.toString());
            log.info("Operación {} {} procesada: {}", tipo, operacionId, estado);
            MDC.remove("operacion_id");
        });
    }
}