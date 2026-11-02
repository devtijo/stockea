package com.stockea.identidad.application;

import com.stockea.identidad.domain.Documento;
import com.stockea.identidad.domain.ErrorIdentidad;
import com.stockea.identidad.domain.ReglaIdentidadException;
import com.stockea.identidad.domain.VerificadorPin;
import com.stockea.identidad.infrastructure.persistence.UsuarioEntity;
import com.stockea.identidad.infrastructure.persistence.UsuarioRepository;
import com.stockea.shared.seguridad.Rol;
import com.stockea.shared.tenant.TenantContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

//el administrador gestiona los usuarios de SU negocio
@Service
@PreAuthorize("hasRole('ADMINISTRADOR')")
public class UsuariosService {

    public record NuevoUsuario(Documento documento, String nombre, String correo, Rol rol,
                               String claveTemporal, String pin) {
    }

    public record UsuarioResumen(UUID id, String tipoDocumento, String numeroDocumento, String nombre,
                                 String correo, Rol rol, boolean activo, Instant creadoEn) {
        static UsuarioResumen de(UsuarioEntity u) {
            return new UsuarioResumen(u.getId(), u.getTipoDocumento(), u.getNumeroDocumento(), u.getNombre(),
                    u.getCorreo(), u.getRol(), u.isActivo(), u.getCreadoEn());
        }
    }

    private final ProveedorIdentidad proveedor;
    private final UsuarioRepository usuarios;
    private final Clock clock;

    public UsuariosService(ProveedorIdentidad proveedor, UsuarioRepository usuarios, Clock clock) {
        this.proveedor = proveedor;
        this.usuarios = usuarios;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<UsuarioResumen> listar() {
        var negocio = TenantContext.requerido().negocioId();
        return usuarios.findByNegocioIdOrderByNombre(negocio).stream().map(UsuarioResumen::de).toList();
    }

    // La clave es temporal: Keycloak obliga a cambiarla en el primer inicio de sesión
    @Transactional
    public UsuarioResumen crear(NuevoUsuario n) {
        var tenant = TenantContext.requerido();
        var pin = VerificadorPin.derivar(n.pin());
        UUID id = proveedor.crearCuenta(new ProveedorIdentidad.CuentaNueva(
                n.documento().nombreDeUsuario(), n.nombre(), n.correo(), n.claveTemporal(), true,
                n.rol(), tenant.negocioId()));
        try {
            var entidad = usuarios.saveAndFlush(new UsuarioEntity(id, tenant.negocioId(), n.documento().tipo().name(),
                    n.documento().numero(), n.nombre(), n.correo(), n.rol(), pin.salBase64(), pin.iteraciones(),
                    pin.hashBase64(), clock.instant(), tenant.usuarioId()));
            return UsuarioResumen.de(entidad);
        } catch (RuntimeException e) {
            proveedor.eliminar(id);
            throw e;
        }
    }

    // impide el acceso sin borrar su historial de operaciones
    @Transactional
    public void inhabilitar(UUID usuarioId) {
        var tenant = TenantContext.requerido();
        if (tenant.usuarioId().equals(usuarioId)) {
            throw new ReglaIdentidadException(ErrorIdentidad.NO_PUEDE_INHABILITARSE);
        }
        var usuario = usuarios.findById(usuarioId)
                .filter(u -> u.getNegocioId().equals(tenant.negocioId()))
                .orElseThrow(() -> new ReglaIdentidadException(ErrorIdentidad.USUARIO_NO_ENCONTRADO));
        proveedor.deshabilitar(usuarioId);
        usuario.inhabilitar();
    }
}
