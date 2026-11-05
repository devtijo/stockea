package com.stockea.inventario.domain;

import com.stockea.shared.domain.CodigoError;

public enum ErrorInventario implements CodigoError {
    PRODUCTO_NO_ENCONTRADO("El producto no existe", Tipo.NO_ENCONTRADO),
    PRODUCTO_INACTIVO("El producto no existe o está inactivo", Tipo.INVALIDO),
    CANTIDAD_ENTRADA_INVALIDA("La cantidad recibida debe ser mayor que cero", Tipo.INVALIDO),
    COSTO_NEGATIVO("El costo no puede ser negativo", Tipo.INVALIDO),
    MERCANCIA_VENCIDA("No se puede recibir mercancía ya vencida", Tipo.INVALIDO),
    PRECIO_INVALIDO("El precio debe ser mayor que cero", Tipo.INVALIDO);

    private final String mensaje;
    private final Tipo tipo;

    ErrorInventario(String mensaje, Tipo tipo) {
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
