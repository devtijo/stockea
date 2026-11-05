package com.stockea.identidad.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;


// Documento de identidad colombiano. Es el nombre de usuario en Keycloak
// el login es tipo + número de documento + contraseña

public record Documento(TipoDocumento tipo, String numero) {

    private static final Pattern PATRON_NUMERO = Pattern.compile("[0-9A-Za-z]{5,15}");

    public enum TipoDocumento { CC, CE, PA, PPT, TI }

    public Documento {
        Objects.requireNonNull(tipo, "tipo");
        Objects.requireNonNull(numero, "numero");
        numero = numero.trim();
        if (!PATRON_NUMERO.matcher(numero).matches()) {
            throw new ReglaIdentidadException(ErrorIdentidad.DOCUMENTO_INVALIDO);
        }
    }
    // Nombre de usuario único global en Keycloak
    public String nombreDeUsuario() {
        return (tipo.name() + "-" + numero).toLowerCase(Locale.ROOT);
    }
}
