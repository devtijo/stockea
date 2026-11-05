package com.stockea.seguridad;

import com.stockea.IntegracionBase;
import com.stockea.shared.seguridad.Rol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Defensa en profundidad en la base de datos: aunque el código tuviera un error,
 * PostgreSQL no deja ver ni tocar datos de otro negocio (ADR-009) ni modificar la
 * historia (ADR-004).
 */

class RlsInmutabilidadIntegrationTest extends IntegracionBase {

    private Sesion a;
    private Sesion b;
    private UUID productoB;

    @BeforeEach
    void dosNegocios() {
        a = crearNegocioCon(Rol.ADMINISTRADOR);
        b = crearNegocioCon(Rol.ADMINISTRADOR);
        enNegocio(a.negocioId(), () -> jdbc.update(
                "INSERT INTO producto (id, negocio_id, nombre, unidad_venta, precio_venta) VALUES (?,?,?,?,?)",
                UUID.randomUUID(), a.negocioId(), "Leche A", "UNIDAD", 4000));
        productoB = UUID.randomUUID();
        enNegocio(b.negocioId(), () -> jdbc.update(
                "INSERT INTO producto (id, negocio_id, nombre, unidad_venta, precio_venta) VALUES (?,?,?,?,?)",
                productoB, b.negocioId(), "Leche B", "UNIDAD", 4000));
    }

    @Test
    @DisplayName("RLS: cada negocio ve solo sus filas; sin negocio en la conexión no se ve nada")
    void cadaNegocioVeLoSuyo() {
        assertThat(enNegocio(a.negocioId(), () -> jdbc.queryForObject("SELECT count(*) FROM producto", Integer.class)))
                .isEqualTo(1);
        assertThat(enNegocio(a.negocioId(), () -> jdbc.queryForObject("SELECT count(*) FROM usuario", Integer.class)))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM producto", Integer.class))
                .as("falla cerrada: sin tenant no hay datos").isZero();
        assertThat(enNegocio(a.negocioId(), () -> jdbc.queryForObject(
                "SELECT count(*) FROM producto WHERE id = ?", Integer.class, productoB))).isZero();
    }

    @Test
    @DisplayName("RLS: no se puede insertar una fila a nombre de otro negocio")
    void noSeEscribeEnOtroNegocio() {
        assertThatThrownBy(() -> enNegocio(a.negocioId(), () -> jdbc.update(
                "INSERT INTO producto (id, negocio_id, nombre, unidad_venta, precio_venta) VALUES (?,?,?,?,?)",
                UUID.randomUUID(), b.negocioId(), "Intruso", "UNIDAD", 1)))
                .rootCause().hasMessageContaining("row-level security");
    }

    @Test
    @DisplayName("FK compuesta: un lote del negocio A no puede apuntar a un producto del negocio B")
    void fkMismoNegocio() {
        assertThatThrownBy(() -> enNegocio(a.negocioId(), () -> jdbc.update(
                "INSERT INTO lote (id, negocio_id, producto_id, fecha_vencimiento, cantidad_inicial, "
                        + "cantidad_disponible, costo_unitario) VALUES (?,?,?,CURRENT_DATE + 5,1,1,1)",
                UUID.randomUUID(), a.negocioId(), productoB)))
                .rootCause().hasMessageContaining("foreign key");
    }

    @Test
    @DisplayName("ADR-004: la app no tiene permiso de UPDATE/DELETE sobre la historia ni DELETE en general")
    void appNoPuedeReescribirLaHistoria() {
        enNegocio(a.negocioId(), () -> {
            assertThatThrownBy(() -> jdbc.update("UPDATE venta SET total = 0")).rootCause().hasMessageContaining("permission denied");
            assertThatThrownBy(() -> jdbc.update("UPDATE consentimiento SET version_politica = 'x'"))
                    .rootCause().hasMessageContaining("permission denied");
            assertThatThrownBy(() -> jdbc.update("DELETE FROM producto")).rootCause().hasMessageContaining("permission denied");
            return null;
        });
    }

    @Test
    @DisplayName("ADR-004: ni siquiera el dueño del esquema puede modificar un registro inmutable (trigger)")
    void triggerProtegeAunqueTenganPermiso() {
        enNegocio(a.negocioId(), () -> jdbc.update(
                "INSERT INTO consentimiento (id, negocio_id, usuario_id, version_politica, aceptado_en) VALUES (?,?,?,?,?)",
                UUID.randomUUID(), a.negocioId(), a.usuarioId(), "2026-10", java.sql.Timestamp.from(Instant.now())));

        try (var conexion = new SingleConnectionDataSource(jdbcUrl(), "stockea_migrator", "test", true)) {
            var dueno = new JdbcTemplate(conexion);
            dueno.queryForObject("SELECT set_config('app.negocio_id', ?, false)", String.class, a.negocioId().toString());
            assertThatThrownBy(() -> dueno.update("UPDATE consentimiento SET version_politica = 'alterada'"))
                    .rootCause().hasMessageContaining("Registro inmutable");
            assertThatThrownBy(() -> dueno.update("DELETE FROM consentimiento"))
                    .rootCause().hasMessageContaining("Registro inmutable");
        }
    }
}
