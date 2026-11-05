package com.stockea.config.security;

import com.stockea.shared.seguridad.Rol;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

//El backend es un resource server: valida en CADA petición el JWT emitido porKeycloak (firma, emisor y vencimiento)
// sin sesión en servidor, Roles acumulativos
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final Set<String> ROLES_STOCKEA =
            Arrays.stream(Rol.values()).map(Enum::name).collect(Collectors.toUnmodifiableSet());

    // java:S4502 (CSRF desactivado): es seguro aquí porque la API no usa cookies de sesión
    // el token viaja en la cabecera Authorization, que un sitio ajeno no puede poner
    @SuppressWarnings("java:S4502")
    @Bean
    SecurityFilterChain api(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // API con token Bearer, sin cookies de sesión
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/publico/negocios").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info",
                                "/actuator/prometheus").permitAll()
                        .requestMatchers("/api/usuarios/**").hasRole(Rol.ADMINISTRADOR.name())
                        .requestMatchers("/api/sync/**", "/api/catalogo/**").hasRole(Rol.CAJERO.name())
                        .requestMatchers("/api/analitica/**", "/api/inventario/**", "/api/descuentos/**",
                                "/api/caja/**", "/api/discrepancias/**").hasRole(Rol.SUPERVISOR.name())
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(o -> o.jwt(jwt -> jwt.jwtAuthenticationConverter(convertidorDeRoles())))
                .addFilterAfter(new TenantFilter(), BearerTokenAuthenticationFilter.class)
                .headers(h -> h.contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'")));
        return http.build();
    }

    // CAJERO ⊂ SUPERVISOR ⊂ ADMINISTRADOR también en las reglas de Spring
    @Bean
    static RoleHierarchy jerarquiaDeRoles() {
        return RoleHierarchyImpl.withDefaultRolePrefix()
                .role(Rol.ADMINISTRADOR.name()).implies(Rol.SUPERVISOR.name())
                .role(Rol.SUPERVISOR.name()).implies(Rol.CAJERO.name())
                .build();
    }

    // Toma los roles de realm_access.roles y deja solo los de STOCKEA
    static JwtAuthenticationConverter convertidorDeRoles() {
        Converter<Jwt, Collection<GrantedAuthority>> roles = jwt -> TenantFilter.roles(jwt.getClaimAsMap("realm_access"))
                .stream()
                .filter(ROLES_STOCKEA::contains)
                .map(r -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r))
                .toList();
        var convertidor = new JwtAuthenticationConverter();
        convertidor.setJwtGrantedAuthoritiesConverter(roles);
        return convertidor;
    }
}
