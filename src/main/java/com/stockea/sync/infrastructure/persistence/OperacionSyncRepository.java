package com.stockea.sync.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OperacionSyncRepository extends JpaRepository<OperacionSyncEntity, OperacionSyncId> {}