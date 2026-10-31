package com.stockea.sync.infrastructure.persistence;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class OperacionSyncId implements Serializable {
    private UUID negocioId;
    private UUID operacionId;

    public OperacionSyncId() {}
    public OperacionSyncId(UUID negocioId, UUID operacionId) {
        this.negocioId = negocioId;
        this.operacionId = operacionId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OperacionSyncId that)) return false;
        return Objects.equals(negocioId, that.negocioId) && Objects.equals(operacionId, that.operacionId);
    }

    @Override
    public int hashCode() { return Objects.hash(negocioId, operacionId); }
}