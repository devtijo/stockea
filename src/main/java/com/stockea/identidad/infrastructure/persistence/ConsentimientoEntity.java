package com.stockea.identidad.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

// Aceptación de la política de datos - Insert only
@Entity
@Immutable
@Table(name = "consentimiento")
public class ConsentimientoEntity {

    @Id
    private UUID id;
    @Column(name = "negocio_id", nullable = false)
    private UUID negocioId;
    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;
    @Column(name = "version_politica", nullable = false, length = 20)
    private String versionPolitica;
    @Column(name = "aceptado_en", nullable = false)
    private Instant aceptadoEn;

    protected ConsentimientoEntity() {
    }

    public ConsentimientoEntity(UUID id, UUID negocioId, UUID usuarioId, String versionPolitica, Instant aceptadoEn) {
        this.id = id;
        this.negocioId = negocioId;
        this.usuarioId = usuarioId;
        this.versionPolitica = versionPolitica;
        this.aceptadoEn = aceptadoEn;
    }
}
