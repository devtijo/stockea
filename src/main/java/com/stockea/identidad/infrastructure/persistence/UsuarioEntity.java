package com.stockea.identidad.infrastructure.persistence;

import com.stockea.shared.seguridad.Rol;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

// Proyección local del usuario de Keycloak (id = "sub" del token)
@Entity
@Table(name = "usuario")
public class UsuarioEntity {

    @Id
    private UUID id;
    @Column(name = "negocio_id", nullable = false)
    private UUID negocioId;
    @Column(name = "tipo_documento", nullable = false, length = 5)
    private String tipoDocumento;
    @Column(name = "numero_documento", nullable = false, length = 20)
    private String numeroDocumento;
    @Column(nullable = false, length = 120)
    private String nombre;
    @Column(length = 160)
    private String correo;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private Rol rol;
    @Column(nullable = false)
    private boolean activo;
    @Column(name = "pin_sal", nullable = false, length = 64)
    private String pinSal;
    @Column(name = "pin_iteraciones", nullable = false)
    private int pinIteraciones;
    @Column(name = "pin_hash", nullable = false, length = 128)
    private String pinHash;
    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;
    @Column(name = "creado_por")
    private UUID creadoPor;

    protected UsuarioEntity() {
    }

    public UsuarioEntity(UUID id, UUID negocioId, String tipoDocumento, String numeroDocumento, String nombre,
                         String correo, Rol rol, String pinSal, int pinIteraciones, String pinHash,
                         Instant creadoEn, UUID creadoPor) {
        this.id = id;
        this.negocioId = negocioId;
        this.tipoDocumento = tipoDocumento;
        this.numeroDocumento = numeroDocumento;
        this.nombre = nombre;
        this.correo = correo;
        this.rol = rol;
        this.activo = true;
        this.pinSal = pinSal;
        this.pinIteraciones = pinIteraciones;
        this.pinHash = pinHash;
        this.creadoEn = creadoEn;
        this.creadoPor = creadoPor;
    }

    public void inhabilitar() { this.activo = false; }

    public UUID getId() { return id; }
    public UUID getNegocioId() { return negocioId; }
    public String getTipoDocumento() { return tipoDocumento; }
    public String getNumeroDocumento() { return numeroDocumento; }
    public String getNombre() { return nombre; }
    public String getCorreo() { return correo; }
    public Rol getRol() { return rol; }
    public boolean isActivo() { return activo; }
    public String getPinSal() { return pinSal; }
    public int getPinIteraciones() { return pinIteraciones; }
    public String getPinHash() { return pinHash; }
    public Instant getCreadoEn() { return creadoEn; }
}
