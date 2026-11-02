package com.stockea.ventas.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ItemVentaRepository extends JpaRepository<ItemVentaEntity, UUID> {

    List<ItemVentaEntity> findByVentaIdOrderByLinea(UUID ventaId);
}
