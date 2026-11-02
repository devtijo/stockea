package com.stockea;

import com.stockea.identidad.application.ProveedorIdentidad;
import com.stockea.identidad.domain.ErrorIdentidad;
import com.stockea.identidad.domain.ReglaIdentidadException;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// Keycloak en memoria para las pruebas; simula la política de contraseñas y los duplicados
public class ProveedorIdentidadFalso implements ProveedorIdentidad {

    public final Map<UUID, CuentaNueva> cuentas = new ConcurrentHashMap<>();
    public final Set<UUID> deshabilitadas = ConcurrentHashMap.newKeySet();
    public final Set<UUID> eliminadas = ConcurrentHashMap.newKeySet();

    @Override
    public UUID crearCuenta(CuentaNueva c) {
        boolean duplicada = cuentas.values().stream().anyMatch(x -> x.nombreDeUsuario().equals(c.nombreDeUsuario()));
        if (duplicada) {
            throw new ReglaIdentidadException(ErrorIdentidad.DOCUMENTO_YA_REGISTRADO);
        }
        if (c.clave().length() < 8 || c.clave().chars().noneMatch(Character::isDigit)) {
            throw new ReglaIdentidadException(ErrorIdentidad.CLAVE_NO_CUMPLE_POLITICA);
        }
        UUID id = UUID.randomUUID();
        cuentas.put(id, c);
        return id;
    }

    @Override
    public void deshabilitar(UUID usuarioId) {
        deshabilitadas.add(usuarioId);
    }

    @Override
    public void eliminar(UUID usuarioId) {
        cuentas.remove(usuarioId);
        eliminadas.add(usuarioId);
    }

    public void reiniciar() {
        cuentas.clear();
        deshabilitadas.clear();
        eliminadas.clear();
    }
}
