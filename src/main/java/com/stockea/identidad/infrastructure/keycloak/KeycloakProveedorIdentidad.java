package com.stockea.identidad.infrastructure.keycloak;

import com.stockea.identidad.application.ProveedorIdentidad;
import com.stockea.identidad.domain.ErrorIdentidad;
import com.stockea.identidad.domain.ReglaIdentidadException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Adaptador del puerto ProveedorIdentidad sobre la API REST de
// administración de Keycloak. Se autentica como cuenta de servicio
// (client credentials) con permisos mínimos: manage/view/query-users.

// El destino es fijo y viene de configuración; nunca de datos del usuario

@Component
@EnableConfigurationProperties(KeycloakPropiedades.class)
class KeycloakProveedorIdentidad implements ProveedorIdentidad {

    private static final Logger log = LoggerFactory.getLogger(KeycloakProveedorIdentidad.class);
    private static final ParameterizedTypeReference<Map<String, Object>> MAPA = new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<Map<String, Object>>> LISTA =
            new ParameterizedTypeReference<>() {};

    private final RestClient http;
    private final KeycloakPropiedades props;
    private final Clock clock;
    private String token;
    private Instant tokenVence = Instant.EPOCH;

    KeycloakProveedorIdentidad(KeycloakPropiedades props, Clock clock) {
        // Timeouts explícitos: si Keycloak no responde, el registro falla rápido en vez de colgar hilos.
        var fabrica = new JdkClientHttpRequestFactory(
                java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        fabrica.setReadTimeout(Duration.ofSeconds(10));
        this.http = RestClient.builder().baseUrl(props.urlAdmin()).requestFactory(fabrica).build();
        this.props = props;
        this.clock = clock;
    }

    @Override
    public UUID crearCuenta(CuentaNueva c) {
        Map<String, Object> usuario = new HashMap<>();
        usuario.put("username", c.nombreDeUsuario());
        usuario.put("enabled", true);
        usuario.put("firstName", c.nombre());
        usuario.put("attributes", Map.of("negocio_id", List.of(c.negocioId().toString())));
        usuario.put("credentials", List.of(Map.of("type", "password", "value", c.clave(), "temporary", c.claveTemporal())));
        if (c.correo() != null && !c.correo().isBlank()) {
            usuario.put("email", c.correo());
            usuario.put("emailVerified", false);
        }

        UUID id;
        try {
            var respuesta = http.post().uri("/admin/realms/{realm}/users", props.realm())
                    .headers(h -> h.setBearerAuth(tokenDeServicio()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(usuario)
                    .retrieve()
                    .toBodilessEntity();
            var location = respuesta.getHeaders().getLocation();
            if (location == null) {
                throw new IllegalStateException("Keycloak no devolvió la ubicación del usuario creado");
            }
            String ubicacion = location.getPath();
            id = UUID.fromString(ubicacion.substring(ubicacion.lastIndexOf('/') + 1));
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.CONFLICT) {
                throw new ReglaIdentidadException(ErrorIdentidad.DOCUMENTO_YA_REGISTRADO);
            }
            if (e.getStatusCode() == HttpStatus.BAD_REQUEST && e.getResponseBodyAsString().contains("invalidPassword")) {
                throw new ReglaIdentidadException(ErrorIdentidad.CLAVE_NO_CUMPLE_POLITICA);
            }
            throw e;
        }

        try {
            // Se busca el rol entre los "asignables" al usuario: así basta con manage-users/view-users
            // y la cuenta de servicio no necesita view-realm (mínimo privilegio).
            List<Map<String, Object>> disponibles = http.get()
                    .uri("/admin/realms/{realm}/users/{id}/role-mappings/realm/available", props.realm(), id)
                    .headers(h -> h.setBearerAuth(tokenDeServicio()))
                    .retrieve().body(LISTA);
            var rol = disponibles == null ? null : disponibles.stream()
                    .filter(r -> c.rol().name().equals(r.get("name"))).findFirst()
                    .orElseThrow(() -> new IllegalStateException("El realm no tiene el rol " + c.rol()));
            http.post().uri("/admin/realms/{realm}/users/{id}/role-mappings/realm", props.realm(), id)
                    .headers(h -> h.setBearerAuth(tokenDeServicio()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(List.of(rol))
                    .retrieve().toBodilessEntity();
        } catch (RuntimeException e) {
            eliminar(id); // no dejar cuentas sin rol
            throw e;
        }
        log.info("Cuenta creada en Keycloak usuario_id={} rol={} negocio_id={}", id, c.rol(), c.negocioId());
        return id;
    }

    @Override
    public void deshabilitar(UUID usuarioId) {
        http.put().uri("/admin/realms/{realm}/users/{id}", props.realm(), usuarioId)
                .headers(h -> h.setBearerAuth(tokenDeServicio()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("enabled", false))
                .retrieve().toBodilessEntity();
        // Cierra sus sesiones abiertas: no podrá renovar el token.
        http.post().uri("/admin/realms/{realm}/users/{id}/logout", props.realm(), usuarioId)
                .headers(h -> h.setBearerAuth(tokenDeServicio()))
                .retrieve().toBodilessEntity();
    }

    @Override
    public void eliminar(UUID usuarioId) {
        try {
            http.delete().uri("/admin/realms/{realm}/users/{id}", props.realm(), usuarioId)
                    .headers(h -> h.setBearerAuth(tokenDeServicio()))
                    .retrieve().toBodilessEntity();
        } catch (RuntimeException e) {
            log.error("No se pudo compensar (eliminar) la cuenta usuario_id={} en Keycloak", usuarioId, e);
        }
    }

    // Token de la cuenta de servicio, reutilizado hasta 30 s antes de vencer
    private synchronized String tokenDeServicio() {
        if (token != null && clock.instant().isBefore(tokenVence)) {
            return token;
        }
        var formulario = new LinkedMultiValueMap<String, String>();
        formulario.add("grant_type", "client_credentials");
        formulario.add("client_id", props.clienteId());
        formulario.add("client_secret", props.clienteSecreto());
        Map<String, Object> respuesta = http.post()
                .uri("/realms/{realm}/protocol/openid-connect/token", props.realm())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(formulario)
                .retrieve().body(MAPA);
        if (respuesta == null || !(respuesta.get("expires_in") instanceof Number expira)) {
            throw new IllegalStateException("Keycloak no devolvió un token de servicio válido");
        }
        token = String.valueOf(respuesta.get("access_token"));
        long segundos = expira.longValue();
        tokenVence = clock.instant().plusSeconds(Math.max(0, segundos - 30));
        return token;
    }
}
