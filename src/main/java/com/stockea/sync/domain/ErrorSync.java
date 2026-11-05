package com.stockea.sync.domain;

import com.stockea.shared.domain.CodigoError;

public enum ErrorSync implements CodigoError {
    TIPO_OPERACION_DESCONOCIDO("El servidor no reconoce la operación %s", Tipo.INVALIDO),
    DISCREPANCIA_NO_ENCONTRADA("La discrepancia no existe o ya está resuelta", Tipo.NO_ENCONTRADO);

    private final String mensaje;
    private final Tipo tipo;

    ErrorSync(String mensaje, Tipo tipo) {
        this.mensaje = mensaje;
        this.tipo = tipo;
    }

    @Override
    public String mensaje() {
        return mensaje;
    }

    @Override
    public Tipo tipo() {
        return tipo;
    }
}
