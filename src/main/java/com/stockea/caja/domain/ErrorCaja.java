package com.stockea.caja.domain;

import com.stockea.shared.domain.CodigoError;

public enum ErrorCaja implements CodigoError {
    APERTURA_INVALIDA("La apertura de caja necesita el turno y una base mayor o igual a cero", Tipo.INVALIDO),
    CIERRE_INVALIDO("El cierre de caja necesita el turno y un monto contado mayor o igual a cero", Tipo.INVALIDO);

    private final String mensaje;
    private final Tipo tipo;

    ErrorCaja(String mensaje, Tipo tipo) {
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
