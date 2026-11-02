package com.stockea.identidad.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ConsentimientoRepository extends JpaRepository<ConsentimientoEntity, UUID> {
}
