package com.stockea.config.web;

import com.stockea.shared.domain.CodigoError;

public enum ErrorGeneral implements CodigoError {
    ACCESO_DENEGADO("No tiene permisos para esta acción", Tipo.INVALIDO),
    TOKEN_SIN_NEGOCIO("El token no pertenece a ningún negocio", Tipo.INVALIDO),
    SOLICITUD_INVALIDA("La solicitud tiene datos inválidos", Tipo.INVALIDO),
    ERROR_INTERNO("Ocurrió un error interno. Intente de nuevo.", Tipo.INVALIDO);

    private final String mensaje;
    private final Tipo tipo;

    ErrorGeneral(String mensaje, Tipo tipo) {
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
