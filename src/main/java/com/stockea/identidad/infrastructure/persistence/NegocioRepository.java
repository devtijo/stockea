package com.stockea.identidad.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface NegocioRepository extends JpaRepository<NegocioEntity, UUID> {
}
