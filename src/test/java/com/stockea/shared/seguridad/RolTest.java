package com.stockea.shared.seguridad;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** ADR-005: roles acumulativos CAJERO ⊂ SUPERVISOR ⊂ ADMINISTRADOR. */
class RolTest {

    @Test
    void losRolesSonAcumulativos() {
        assertThat(Rol.ADMINISTRADOR.incluye(Rol.CAJERO)).isTrue();
        assertThat(Rol.SUPERVISOR.incluye(Rol.CAJERO)).isTrue();
        assertThat(Rol.CAJERO.incluye(Rol.SUPERVISOR)).isFalse();
        assertThat(Rol.SUPERVISOR.incluye(Rol.ADMINISTRADOR)).isFalse();
    }

    @Test
    void delTokenSeTomaElRolMayorEIgnoraRolesAjenos() {
        assertThat(Rol.mayor(List.of("offline_access", "CAJERO", "SUPERVISOR"))).hasValue(Rol.SUPERVISOR);
        assertThat(Rol.mayor(List.of("default-roles-stockea", "uma_authorization"))).isEmpty();
    }
}
