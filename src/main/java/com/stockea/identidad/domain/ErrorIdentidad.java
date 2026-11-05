package com.stockea.identidad.domain;

public enum ErrorIdentidad {
    POLITICA_NO_ACEPTADA("Debe aceptar la política de tratamiento de datos (Ley 1581 de 2012)"),
    PIN_INVALIDO("El PIN debe tener entre 4 y 6 dígitos"),
    DOCUMENTO_YA_REGISTRADO("Ya existe un usuario con ese documento"),
    CLAVE_NO_CUMPLE_POLITICA("La contraseña no cumple la política: mínimo 8 caracteres, al menos un dígito y distinta del documento"),
    USUARIO_INHABILITADO("El usuario fue inhabilitado; inicie sesión con otra cuenta"),
    USUARIO_NO_ENCONTRADO("El usuario no existe en este negocio"),
    NO_PUEDE_INHABILITARSE("Un administrador no puede inhabilitarse a sí mismo"),
    DOCUMENTO_INVALIDO("Número de documento inválido: solo letras y números, de 5 a 15 caracteres"),
    DISPOSITIVO_BLOQUEADO("El dispositivo está bloqueado para este negocio");

    private final String mensaje;

    ErrorIdentidad(String mensaje) {
        this.mensaje = mensaje;
    }

    public String mensaje() {
        return mensaje;
    }
}
