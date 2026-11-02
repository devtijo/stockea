package com.stockea.identidad.domain;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.regex.Pattern;

//Material de verificación local del PIN
//El dispositivo lo recibe al iniciar sesión en línea y verifica el
//PIN sin conexión con WebCrypto usando los mismos parámetros

public final class VerificadorPin {

    public static final int ITERACIONES = 210_000;
    private static final int BITS = 256;
    private static final Pattern PIN = Pattern.compile("\\d{4,6}");
    private static final SecureRandom ALEATORIO = new SecureRandom();

    public record MaterialPin(String salBase64, int iteraciones, String hashBase64) {
    }

    private VerificadorPin() {
    }

    public static MaterialPin derivar(String pin) {
        validar(pin);
        byte[] sal = new byte[16];
        ALEATORIO.nextBytes(sal);
        byte[] hash = pbkdf2(pin, sal, ITERACIONES);
        return new MaterialPin(b64(sal), ITERACIONES, b64(hash));
    }

    public static boolean coincide(String pin, MaterialPin material) {
        if (pin == null || !PIN.matcher(pin).matches()) {
            return false;
        }
        byte[] esperado = Base64.getDecoder().decode(material.hashBase64());
        byte[] calculado = pbkdf2(pin, Base64.getDecoder().decode(material.salBase64()), material.iteraciones());
        return MessageDigest.isEqual(esperado, calculado); // comparación en tiempo constante
    }

    public static void validar(String pin) {
        if (pin == null || !PIN.matcher(pin).matches()) {
            throw new ReglaIdentidadException(ErrorIdentidad.PIN_INVALIDO);
        }
    }

    private static byte[] pbkdf2(String pin, byte[] sal, int iteraciones) {
        var spec = new PBEKeySpec(pin.toCharArray(), sal, iteraciones, BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2 no disponible", e);
        } finally {
            spec.clearPassword();
        }
    }

    private static String b64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }
}
