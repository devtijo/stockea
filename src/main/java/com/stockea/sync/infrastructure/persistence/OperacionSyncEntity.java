package com.stockea.sync.infrastructure.persistence;

import com.stockea.sync.api.EstadoOperacion;
import com.stockea.sync.api.TipoOperacion;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "operacion_sync")
@IdClass(OperacionSyncId.class)
public class OperacionSyncEntity {

    @Id @Column(name = "negocio_id") private UUID negocioId;
    @Id @Column(name = "operacion_id") private UUID operacionId;

    @Column(name = "dispositivo_id", nullable = false) private UUID dispositivoId;
    @Column(name = "usuario_id", nullable = false) private UUID usuarioId;

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private TipoOperacion tipo;
    @Column(name = "secuencia_dispositivo", nullable = false) private Long secuenciaDispositivo;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private EstadoOperacion estado;

    @Column(name = "payload", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String payload;

    @Column(name = "creada_en_cliente", nullable = false) private Instant creadaEnCliente;
    @Column(name = "recibida_en", nullable = false) private Instant recibidaEn;
    @Column(name = "procesada_en") private Instant procesadaEn;
    @Column(name = "resultado_id") private UUID resultadoId;

    public OperacionSyncEntity() {}

    public UUID getNegocioId() { return negocioId; } public void setNegocioId(UUID v) { this.negocioId = v; }
    public UUID getOperacionId() { return operacionId; } public void setOperacionId(UUID v) { this.operacionId = v; }
    public UUID getDispositivoId() { return dispositivoId; } public void setDispositivoId(UUID v) { this.dispositivoId = v; }
    public UUID getUsuarioId() { return usuarioId; } public void setUsuarioId(UUID v) { this.usuarioId = v; }
    public TipoOperacion getTipo() { return tipo; } public void setTipo(TipoOperacion v) { this.tipo = v; }
    public Long getSecuenciaDispositivo() { return secuenciaDispositivo; } public void setSecuenciaDispositivo(Long v) { this.secuenciaDispositivo = v; }
    public EstadoOperacion getEstado() { return estado; } public void setEstado(EstadoOperacion v) { this.estado = v; }
    public String getPayload() { return payload; } public void setPayload(String v) { this.payload = v; }
    public Instant getCreadaEnCliente() { return creadaEnCliente; } public void setCreadaEnCliente(Instant v) { this.creadaEnCliente = v; }
    public Instant getRecibidaEn() { return recibidaEn; } public void setRecibidaEn(Instant v) { this.recibidaEn = v; }
    public Instant getProcesadaEn() { return procesadaEn; } public void setProcesadaEn(Instant v) { this.procesadaEn = v; }
    public UUID getResultadoId() { return resultadoId; } public void setResultadoId(UUID v) { this.resultadoId = v; }
}