package com.stockea.identidad.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.stockea.identidad.domain.Documento;
import com.stockea.identidad.domain.ErrorIdentidad;
import com.stockea.identidad.domain.ReglaIdentidadException;
import com.stockea.identidad.domain.VerificadorPin;
import com.stockea.identidad.infrastructure.persistence.ConsentimientoEntity;
import com.stockea.identidad.infrastructure.persistence.ConsentimientoRepository;
import com.stockea.identidad.infrastructure.persistence.NegocioEntity;
import com.stockea.identidad.infrastructure.persistence.NegocioRepository;
import com.stockea.identidad.infrastructure.persistence.UsuarioEntity;
import com.stockea.identidad.infrastructure.persistence.UsuarioRepository;
import com.stockea.shared.seguridad.Rol;
import com.stockea.shared.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.UUID;

// Toca dos sistemas (Keycloak y PostgreSQL) sin transacción distribuida: primero
// crea la cuenta en Keycloak y, si la escritura en la base falla, la elimina
// (compensación). Así nunca queda un negocio sin dueño ni una cuenta sin negocio.

@Service
public class RegistroNegocioService {

    public static final String ZONA_POR_DEFECTO = "America/Bogota";
    private static final BigDecimal DESCUENTO_MAXIMO_POR_DEFECTO = new BigDecimal("30.00");

    public record Solicitud(String nombreNegocio, String tipoNegocio, String ciudad, String direccion,
                            Documento documento, String nombreAdministrador, String correo,
                            String clave, String pin, boolean aceptaPolitica, String versionPolitica) {
    }

    public record Registro(UUID negocioId, UUID usuarioId, String nombreDeUsuario) {
    }

    private final ProveedorIdentidad proveedor;
    private final NegocioRepository negocios;
    private final UsuarioRepository usuarios;
    private final ConsentimientoRepository consentimientos;
    private final TransactionTemplate tx;
    private final Clock clock;

    public RegistroNegocioService(ProveedorIdentidad proveedor, NegocioRepository negocios, UsuarioRepository usuarios,
                                  ConsentimientoRepository consentimientos, TransactionTemplate tx, Clock clock) {
        this.proveedor = proveedor;
        this.negocios = negocios;
        this.usuarios = usuarios;
        this.consentimientos = consentimientos;
        this.tx = tx;
        this.clock = clock;
    }

    public Registro registrar(Solicitud s) {
        if (!s.aceptaPolitica()) {
            throw new ReglaIdentidadException(ErrorIdentidad.POLITICA_NO_ACEPTADA);
        }
        var pin = VerificadorPin.derivar(s.pin());
        UUID negocioId = UuidCreator.getTimeOrderedEpoch();
        String usuario = s.documento().nombreDeUsuario();

        UUID usuarioId = proveedor.crearCuenta(new ProveedorIdentidad.CuentaNueva(
                usuario, s.nombreAdministrador(), s.correo(), s.clave(), false, Rol.ADMINISTRADOR, negocioId));

        try {
            var tenant = new TenantContext.Tenant(negocioId, usuarioId, Rol.ADMINISTRADOR);
            TenantContext.ejecutarComo(tenant, () -> tx.execute(estado -> {
                var ahora = clock.instant();
                negocios.save(new NegocioEntity(negocioId, s.nombreNegocio(), s.tipoNegocio(), s.ciudad(),
                        s.direccion(), ZONA_POR_DEFECTO, DESCUENTO_MAXIMO_POR_DEFECTO, ahora));
                usuarios.save(new UsuarioEntity(usuarioId, negocioId, s.documento().tipo().name(),
                        s.documento().numero(), s.nombreAdministrador(), s.correo(), Rol.ADMINISTRADOR,
                        pin.salBase64(), pin.iteraciones(), pin.hashBase64(), ahora, null));
                consentimientos.save(new ConsentimientoEntity(UuidCreator.getTimeOrderedEpoch(), negocioId,
                        usuarioId, s.versionPolitica(), ahora));
                return null;
            }));
        } catch (RuntimeException e) {
            proveedor.eliminar(usuarioId);
            throw e;
        }
        return new Registro(negocioId, usuarioId, usuario);
    }
}
