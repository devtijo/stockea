package com.stockea.identidad.infrastructure.web;

import com.stockea.identidad.domain.Documento;
import com.stockea.shared.seguridad.Rol;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

// Contratos HTTP del módulo identidad, validados con Bean Validation
final class IdentidadDtos {

    private IdentidadDtos() {
    }

    record DocumentoDto(@NotNull Documento.TipoDocumento tipo,
                        @NotBlank @Pattern(regexp = "[0-9A-Za-z]{5,15}") String numero) {
        Documento aDominio() {
            return new Documento(tipo, numero);
        }
    }

    record RegistroNegocioRequest(
            @NotNull @Valid NegocioDto negocio,
            @NotNull @Valid AdministradorDto administrador,
            @AssertTrue(message = "Debe aceptar la política de tratamiento de datos") boolean aceptaPolitica,
            @NotBlank @Size(max = 20) String versionPolitica) {
    }

    record NegocioDto(@NotBlank @Size(max = 120) String nombre,
                      @NotBlank @Size(max = 40) String tipo,
                      @NotBlank @Size(max = 80) String ciudad,
                      @NotBlank @Size(max = 160) String direccion) {
    }

    record AdministradorDto(@NotNull @Valid DocumentoDto documento,
                            @NotBlank @Size(max = 120) String nombre,
                            @Email @Size(max = 160) String correo,
                            @NotBlank @Size(min = 8, max = 64) String clave,
                            @NotBlank @Pattern(regexp = "\\d{4,6}") String pin) {
    }

    record RegistroNegocioResponse(UUID negocioId, UUID usuarioId, String usuario) {
    }

    record NuevoUsuarioRequest(@NotNull @Valid DocumentoDto documento,
                               @NotBlank @Size(max = 120) String nombre,
                               @Email @Size(max = 160) String correo,
                               @NotNull Rol rol,
                               @NotBlank @Size(min = 8, max = 64) String claveTemporal,
                               @NotBlank @Pattern(regexp = "\\d{4,6}") String pin) {
    }
}
