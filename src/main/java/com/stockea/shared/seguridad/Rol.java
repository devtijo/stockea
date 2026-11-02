package com.stockea.shared.seguridad;

import java.util.Collection;
import java.util.Optional;

//Roles acumulativos: CAJERO ⊂ SUPERVISOR ⊂ ADMINISTRADOR

public enum Rol {
    CAJERO(1),
    SUPERVISOR(2),
    ADMINISTRADOR(3);

    private final int nivel;

    Rol(int nivel) {
        this.nivel = nivel;
    }

    public boolean incluye(Rol otro) {
        return this.nivel >= otro.nivel;
    }

    // El rol de mayor nivel entre los nombres recibidos.
    public static Optional<Rol> mayor(Collection<String> nombres) {
        Rol mayor = null;
        for (String nombre : nombres) {
            for (Rol r : values()) {
                if (r.name().equals(nombre) && (mayor == null || r.nivel > mayor.nivel)) {
                    mayor = r;
                }
            }
        }
        return Optional.ofNullable(mayor);
    }
}
