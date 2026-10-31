package com.stockea.sync.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface DiscrepanciaRepository extends JpaRepository<DiscrepanciaEntity, UUID> {}