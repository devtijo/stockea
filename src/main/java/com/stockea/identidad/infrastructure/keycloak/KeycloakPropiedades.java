package com.stockea.identidad.infrastructure.keycloak;

import org.springframework.boot.context.properties.ConfigurationProperties;

// Conexión del backend con la API de administración de Keycloak
@ConfigurationProperties("stockea.keycloak")
public record KeycloakPropiedades(String urlAdmin, String realm, String clienteId, String clienteSecreto) {
}
