package com.stockea.ventas.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface VentaRepository extends JpaRepository<VentaEntity, UUID> {}