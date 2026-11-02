package com.stockea.identidad.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "negocio")
public class NegocioEntity {

    @Id
    private UUID id;
    @Column(nullable = false, length = 120)
    private String nombre;
    @Column(nullable = false, length = 40)
    private String tipo;
    @Column(nullable = false, length = 80)
    private String ciudad;
    @Column(nullable = false, length = 160)
    private String direccion;
    @Column(name = "zona_horaria", nullable = false, length = 40)
    private String zonaHoraria;
    @Column(name = "porcentaje_max_descuento", nullable = false)
    private BigDecimal porcentajeMaxDescuento;
    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    protected NegocioEntity() {
    }

    public NegocioEntity(UUID id, String nombre, String tipo, String ciudad, String direccion,
                         String zonaHoraria, BigDecimal porcentajeMaxDescuento, Instant creadoEn) {
        this.id = id;
        this.nombre = nombre;
        this.tipo = tipo;
        this.ciudad = ciudad;
        this.direccion = direccion;
        this.zonaHoraria = zonaHoraria;
        this.porcentajeMaxDescuento = porcentajeMaxDescuento;
        this.creadoEn = creadoEn;
    }

    public UUID getId() { return id; }
    public String getNombre() { return nombre; }
    public String getTipo() { return tipo; }
    public String getCiudad() { return ciudad; }
    public String getZonaHoraria() { return zonaHoraria; }
    public BigDecimal getPorcentajeMaxDescuento() { return porcentajeMaxDescuento; }
}
