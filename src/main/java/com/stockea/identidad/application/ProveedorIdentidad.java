package com.stockea.identidad.application;

import com.stockea.shared.seguridad.Rol;

import java.util.UUID;

public interface ProveedorIdentidad {

    record CuentaNueva(String nombreDeUsuario, String nombre, String correo, String clave,
                       boolean claveTemporal, Rol rol, UUID negocioId) {
    }

    //Crea la cuenta con su rol y su negocio; devuelve el id (el "sub" del token)
    UUID crearCuenta(CuentaNueva cuenta);

    void deshabilitar(UUID usuarioId);

    //Compensación: borra una cuenta recién creada si falla el resto del registro
    void eliminar(UUID usuarioId);
}
