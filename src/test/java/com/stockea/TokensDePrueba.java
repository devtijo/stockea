package com.stockea;

import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Tokens de prueba: reemplazan a Keycloak en las pruebas de integración.
// Formato: "prueba~negocioId~usuarioId~ROL1.ROL2" (negocioId puede ir vacío). Solo usa caracteres válidos en un Bearer token (RFC 6750), si no Spring lo rechaza antes de decodificar.
// Cualquier otro valor se rechaza como token inválido (401).

public final class TokensDePrueba {

    private TokensDePrueba() {
    }

    public static String token(UUID negocio, UUID usuario, String... roles) {
        return "prueba~" + (negocio == null ? "" : negocio) + "~" + usuario + "~" + String.join(".", roles);
    }

    public static Jwt decodificar(String token) {
        String[] partes = token.split("~", -1);
        if (partes.length != 4 || !partes[0].equals("prueba")) {
            throw new BadJwtException("Token inválido");
        }
        var ahora = Instant.now();
        var jwt = Jwt.withTokenValue(token)
                .header("alg", "none")
                .issuer("http://keycloak-de-prueba/realms/stockea")
                .subject(partes[2])
                .issuedAt(ahora)
                .expiresAt(ahora.plusSeconds(900))
                .claim("realm_access", Map.of("roles", List.of(Arrays.stream(partes[3].split("\\.")).toArray())));
        if (!partes[1].isEmpty()) {
            jwt.claim("negocio_id", partes[1]);
        }
        return jwt.build();
    }
}
