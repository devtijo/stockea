package com.stockea.identidad.api;

import java.util.UUID;

// Registro y control de los equipos POS de un negocio
public interface DispositivoApi {
    void registrarUso(UUID negocioId, UUID dispositivoId, UUID usuarioId);
    class AccesoRevocadoException extends RuntimeException {
        public AccesoRevocadoException(String motivo) {
            super(motivo);
        }
    }
}
