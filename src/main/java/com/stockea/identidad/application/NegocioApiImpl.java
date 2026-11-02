package com.stockea.identidad.application;

import com.stockea.identidad.api.NegocioApi;
import com.stockea.identidad.infrastructure.persistence.NegocioRepository;
import com.stockea.identidad.infrastructure.persistence.UsuarioEntity;
import com.stockea.identidad.infrastructure.persistence.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
class NegocioApiImpl implements NegocioApi {

    private final NegocioRepository negocios;
    private final UsuarioRepository usuarios;

    NegocioApiImpl(NegocioRepository negocios, UsuarioRepository usuarios) {
        this.negocios = negocios;
        this.usuarios = usuarios;
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal porcentajeMaximoDescuento(UUID negocioId) {
        return negocios.findById(negocioId)
                .map(n -> n.getPorcentajeMaxDescuento())
                .orElse(BigDecimal.ZERO);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean esUsuarioDelNegocio(UUID negocioId, UUID usuarioId) {
        // RLS ya limita la consulta al negocio de la conexión; se compara igual por claridad
        return usuarioId != null && usuarios.findById(usuarioId)
                .filter(u -> u.getNegocioId().equals(negocioId))
                .isPresent();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, String> nombres(UUID negocioId, Collection<UUID> ids) {
        return usuarios.findAllById(ids).stream()
                .filter(u -> u.getNegocioId().equals(negocioId))
                .collect(Collectors.toMap(UsuarioEntity::getId, UsuarioEntity::getNombre));
    }
}
