package com.stockea.shared.domain;

public class ReglaNegocioException extends RuntimeException {

    private final transient CodigoError error;

    public ReglaNegocioException(CodigoError error, Object... argumentos) {
        super(argumentos.length == 0 ? error.mensaje() : error.mensaje().formatted(argumentos));
        this.error = error;
    }

    public CodigoError error() {
        return error;
    }
}
