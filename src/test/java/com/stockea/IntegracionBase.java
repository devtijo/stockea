package com.stockea;

import com.stockea.shared.seguridad.Rol;
import com.stockea.shared.tenant.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Base de las pruebas de integración: PostgreSQL real con los MISMOS roles que
 * producción (la app conecta sin privilegios de dueño, así RLS y REVOKE se
 * prueban de verdad), tokens de prueba en lugar de Keycloak y un proveedor de
 * identidad en memoria.
 * <p>
 * Por defecto levanta PostgreSQL con Testcontainers (requiere Docker). Si existe
 * la variable STOCKEA_IT_JDBC_URL, usa esa base (con los roles de
 * src/test/resources/db/init/01-roles-test.sql ya creados).
 */

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(IntegracionBase.ConfiguracionPruebas.class)
public abstract class IntegracionBase {

    private static final String URL_EXTERNA = System.getenv("STOCKEA_IT_JDBC_URL");
    private static final PostgreSQLContainer POSTGRES = URL_EXTERNA != null ? null : iniciarPostgres();

    private static PostgreSQLContainer iniciarPostgres() {
        var contenedor = new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
                .withCopyFileToContainer(MountableFile.forClasspathResource("db/init/01-roles-test.sql"),
                        "/docker-entrypoint-initdb.d/01-roles-test.sql");
        contenedor.start();
        return contenedor;
    }

    protected static String jdbcUrl() {
        return URL_EXTERNA != null ? URL_EXTERNA
                : "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432) + "/stockea";
    }

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", IntegracionBase::jdbcUrl);
        r.add("spring.datasource.username", () -> "stockea_app");
        r.add("spring.datasource.password", () -> "test");
        r.add("spring.flyway.user", () -> "stockea_migrator");
        r.add("spring.flyway.password", () -> "test");
        r.add("stockea.keycloak.cliente-secreto", () -> "no-se-usa-en-pruebas");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ConfiguracionPruebas {
        @Bean
        JwtDecoder jwtDecoderDePrueba() {
            return TokensDePrueba::decodificar;
        }

        @Bean
        @Primary
        ProveedorIdentidadFalso proveedorIdentidadFalso() {
            return new ProveedorIdentidadFalso();
        }
    }

    // Un negocio con un usuario ya registrado (como si viniera de Keycloak)
    public record Sesion(UUID negocioId, UUID usuarioId, Rol rol, String token) {
    }

    @Value("${local.server.port}")
    protected int puerto;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected ProveedorIdentidadFalso proveedor;

    protected final HttpClient http = HttpClient.newHttpClient();
    // Conexión del dueño del esquema: solo para limpiar entre pruebas
    protected JdbcTemplate migrador;

    @BeforeEach
    void limpiarBase() {
        if (migrador == null) {
            migrador = new JdbcTemplate(new DriverManagerDataSource(jdbcUrl(), "stockea_migrator", "test"));
        }
        migrador.execute("TRUNCATE discrepancia, asignacion_lote, item_venta, venta, movimiento_inventario, "
                + "operacion_sync, historial_precio, lote, producto, dispositivo, consentimiento, usuario, negocio CASCADE");
        proveedor.reiniciar();
    }

    protected Sesion crearNegocioCon(Rol rol) {
        UUID negocio = UUID.randomUUID();
        UUID usuario = UUID.randomUUID();
        enNegocio(negocio, () -> {
            jdbc.update("INSERT INTO negocio (id, nombre, tipo, ciudad, direccion) VALUES (?,?,?,?,?)",
                    negocio, "Tienda " + negocio.toString().substring(0, 4), "TIENDA", "Rionegro", "Calle 1");
            return null;
        });
        return agregarUsuario(negocio, usuario, rol);
    }

    protected Sesion agregarUsuario(UUID negocio, UUID usuario, Rol rol) {
        enNegocio(negocio, () -> jdbc.update("""
                INSERT INTO usuario (id, negocio_id, tipo_documento, numero_documento, nombre, rol,
                                     pin_sal, pin_iteraciones, pin_hash)
                VALUES (?,?,?,?,?,?,?,?,?)""",
                usuario, negocio, "CC", String.valueOf(Math.abs(usuario.getMostSignificantBits() % 1_000_000_000L)),
                "Usuario " + rol, rol.name(), "c2Fs", 210000, "aGFzaA=="));
        return new Sesion(negocio, usuario, rol, TokensDePrueba.token(negocio, usuario, rol.name()));
    }

    // Ejecuta con app.negocio_id fijado: las consultas ven solo ese negocio (RLS)
    protected <T> T enNegocio(UUID negocio, Supplier<T> trabajo) {
        return TenantContext.ejecutarComo(new TenantContext.Tenant(negocio, UUID.randomUUID(), Rol.ADMINISTRADOR), trabajo);
    }

    protected HttpResponse<String> post(String ruta, String token, String json, String... cabeceras) throws Exception {
        var req = solicitud(ruta, token, cabeceras).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json));
        return http.send(req.build(), HttpResponse.BodyHandlers.ofString());
    }

    protected HttpResponse<String> get(String ruta, String token) throws Exception {
        return http.send(solicitud(ruta, token).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest.Builder solicitud(String ruta, String token, String... cabeceras) {
        var b = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta));
        if (token != null) {
            b.header("Authorization", "Bearer " + token);
        }
        for (int i = 0; i + 1 < cabeceras.length; i += 2) {
            b.header(cabeceras[i], cabeceras[i + 1]);
        }
        return b;
    }

    protected static Instant ahoraMenos(long segundos) {
        return Instant.now().minusSeconds(segundos);
    }
}
