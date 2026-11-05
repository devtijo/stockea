package com.stockea.descuentos.domain;

import com.stockea.shared.domain.CodigoError;

public enum ErrorDescuento implements CodigoError {
    PORCENTAJE_FUERA_DE_RANGO("El descuento debe estar entre 0 y %s %%", Tipo.INVALIDO),
    LOTE_NO_ENCONTRADO("El lote no existe en este negocio", Tipo.NO_ENCONTRADO),
    DESCUENTO_NO_ENCONTRADO("El descuento no existe o ya estaba inactivo", Tipo.NO_ENCONTRADO);

    private final String mensaje;
    private final Tipo tipo;

    ErrorDescuento(String mensaje, Tipo tipo) {
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
