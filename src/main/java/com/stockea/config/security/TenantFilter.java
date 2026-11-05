package com.stockea.config.security;

import com.stockea.config.web.ErrorGeneral;
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

    // Claim del token y campos del log (MDC) con el mismo nombre
    private static final String NEGOCIO_ID = "negocio_id";
    private static final String USUARIO_ID = "usuario_id";

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
            res.sendError(HttpStatus.FORBIDDEN.value(), ErrorGeneral.TOKEN_SIN_NEGOCIO.mensaje());
            return;
        }

        TenantContext.establecer(tenant.get());
        MDC.put(NEGOCIO_ID, tenant.get().negocioId().toString());
        MDC.put(USUARIO_ID, tenant.get().usuarioId().toString());
        try {
            cadena.doFilter(req, res);
        } finally {
            TenantContext.limpiar();
            MDC.remove(NEGOCIO_ID);
            MDC.remove(USUARIO_ID);
        }
    }

    static Optional<TenantContext.Tenant> aTenant(JwtAuthenticationToken auth) {
        try {
            var jwt = auth.getToken();
            UUID negocio = UUID.fromString(jwt.getClaimAsString(NEGOCIO_ID));
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
