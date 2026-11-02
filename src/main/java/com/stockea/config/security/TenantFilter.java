package com.stockea.config.security;

import com.stockea.shared.seguridad.Rol;
import com.stockea.shared.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// Traduce el token ya validado a un TenantContext: negocio y usuario
// salen SOLO del JWT (Zero Trust). Un token válido sin negocio_id es rechazado con 403.

class TenantFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain cadena)
            throws ServletException, IOException {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken jwtAuth)) {
            cadena.doFilter(req, res);
            return;
        }

        Optional<TenantContext.Tenant> tenant = aTenant(jwtAuth);
        if (tenant.isEmpty()) {
            res.sendError(HttpStatus.FORBIDDEN.value(), "El token no pertenece a ningún negocio");
            return;
        }

        TenantContext.establecer(tenant.get());
        MDC.put("negocio_id", tenant.get().negocioId().toString());
        MDC.put("usuario_id", tenant.get().usuarioId().toString());
        try {
            cadena.doFilter(req, res);
        } finally {
            TenantContext.limpiar();
            MDC.remove("negocio_id");
            MDC.remove("usuario_id");
        }
    }

    static Optional<TenantContext.Tenant> aTenant(JwtAuthenticationToken auth) {
        try {
            var jwt = auth.getToken();
            UUID negocio = UUID.fromString(jwt.getClaimAsString("negocio_id"));
            UUID usuario = UUID.fromString(jwt.getSubject());
            Optional<Rol> rol = Rol.mayor(roles(jwt.getClaimAsMap("realm_access")));
            return rol.map(r -> new TenantContext.Tenant(negocio, usuario, r));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    static List<String> roles(Map<String, Object> realmAccess) {
        if (realmAccess == null || !(realmAccess.get("roles") instanceof Collection<?> lista)) {
            return List.of();
        }
        return lista.stream().map(String::valueOf).toList();
    }
}
