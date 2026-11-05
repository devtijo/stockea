package com.stockea.shared.domain;

// Mismo patrón que ErrorDominio: el código es el nombre del enum y el texto vive en el enum, no regado por el código.
public interface CodigoError {

    String name();

    String mensaje();

    Tipo tipo();

    enum Tipo { INVALIDO, NO_ENCONTRADO, CONFLICTO }
}
