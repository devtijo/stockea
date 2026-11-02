package com.stockea.identidad.application;

import com.stockea.identidad.domain.ErrorIdentidad;
import com.stockea.identidad.domain.ReglaIdentidadException;
import com.stockea.identidad.infrastructure.persistence.NegocioRepository;
import com.stockea.identidad.infrastructure.persistence.UsuarioRepository;
import com.stockea.shared.seguridad.Rol;
import com.stockea.shared.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

// Perfil del usuario autenticado. Incluye el material de verificación del PIN
// para que el dispositivo pueda desbloquearse sin conexión

@Service
public class PerfilService {

    public record Perfil(UUID usuarioId, String nombre, Rol rol, UUID negocioId, String negocio,
                         String zonaHoraria, MaterialPinDto pin) {
    }

    public record MaterialPinDto(String sal, int iteraciones, String hash) {
    }

    private final UsuarioRepository usuarios;
    private final NegocioRepository negocios;

    public PerfilService(UsuarioRepository usuarios, NegocioRepository negocios) {
        this.usuarios = usuarios;
        this.negocios = negocios;
    }

    @Transactional(readOnly = true)
    public Perfil actual() {
        var tenant = TenantContext.requerido();
        var usuario = usuarios.findById(tenant.usuarioId())
                .filter(u -> u.getNegocioId().equals(tenant.negocioId()))
                .orElseThrow(() -> new ReglaIdentidadException(ErrorIdentidad.USUARIO_NO_ENCONTRADO));
        if (!usuario.isActivo()) {
            throw new ReglaIdentidadException(ErrorIdentidad.USUARIO_INHABILITADO);
        }
        var negocio = negocios.findById(tenant.negocioId())
                .orElseThrow(() -> new ReglaIdentidadException(ErrorIdentidad.USUARIO_NO_ENCONTRADO));
        return new Perfil(usuario.getId(), usuario.getNombre(), usuario.getRol(), negocio.getId(),
                negocio.getNombre(), negocio.getZonaHoraria(),
                new MaterialPinDto(usuario.getPinSal(), usuario.getPinIteraciones(), usuario.getPinHash()));
    }
}
