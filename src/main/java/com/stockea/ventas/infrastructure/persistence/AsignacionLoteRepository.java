package com.stockea.ventas.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface AsignacionLoteRepository extends JpaRepository<AsignacionLoteEntity, UUID> {}