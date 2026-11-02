package com.stockea.identidad.domain;

/** Regla de negocio de identidad incumplida. La capa web la traduce a 400/404/409. */
public class ReglaIdentidadException extends RuntimeException {

    private final ErrorIdentidad error;

    public ReglaIdentidadException(ErrorIdentidad error) {
        super(error.mensaje());
        this.error = error;
    }

    public ErrorIdentidad error() {
        return error;
    }
}
