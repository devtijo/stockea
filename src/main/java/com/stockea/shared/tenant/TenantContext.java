package com.stockea.shared.tenant;

import com.stockea.shared.seguridad.Rol;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

// Negocio y usuario de la petición en curso. Se llena SOLO a partir del token
//(nunca del cuerpo de la petición) y lo lee la capa de datos para fijar el filtro de Row-Level Security

public final class TenantContext {

    public record Tenant(UUID negocioId, UUID usuarioId, Rol rol) {
        public Tenant {
            Objects.requireNonNull(negocioId, "negocioId");
            Objects.requireNonNull(usuarioId, "usuarioId");
            Objects.requireNonNull(rol, "rol");
        }
    }

    private static final ThreadLocal<Tenant> ACTUAL = new ThreadLocal<>();

    private TenantContext() {
    }

    public static Optional<Tenant> actual() {
        return Optional.ofNullable(ACTUAL.get());
    }

    // El tenant de la petición; falla si no hay (programación defensiva)
    public static Tenant requerido() {
        return actual().orElseThrow(() -> new IllegalStateException("No hay negocio en el contexto de la petición"));
    }

    public static void establecer(Tenant tenant) {
        ACTUAL.set(Objects.requireNonNull(tenant));
    }

    public static void limpiar() {
        ACTUAL.remove();
    }

    //Ejecuta trabajo como si la petición fuera de  tenant y restaura el contexto anterior. Se usa cuando aún no hay token, por ejemplo
    // al registrar un negocio nuevo.


    public static <T> T ejecutarComo(Tenant tenant, Supplier<T> trabajo) {
        Tenant anterior = ACTUAL.get();
        ACTUAL.set(Objects.requireNonNull(tenant));
        try {
            return trabajo.get();
        } finally {
            if (anterior == null) {
                ACTUAL.remove();
            } else {
                ACTUAL.set(anterior);
            }
        }
    }
}
