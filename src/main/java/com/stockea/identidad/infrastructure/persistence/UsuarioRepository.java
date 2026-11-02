package com.stockea.identidad.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

// RLS ya limita las filas al negocio del token el filtro explícito es la primera capa
public interface UsuarioRepository extends JpaRepository<UsuarioEntity, UUID> {
    List<UsuarioEntity> findByNegocioIdOrderByNombre(java.util.UUID negocioId);
}
