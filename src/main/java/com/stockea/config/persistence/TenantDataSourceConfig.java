package com.stockea.config.persistence;

import com.stockea.shared.tenant.TenantContext;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DelegatingDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

// Fija app.negocio_id en cada conexión que la aplicación toma del pool.
// PostgreSQL usa ese valor en las políticas de Row-Level Security (V4), así que
//aunque una consulta olvide filtrar por negocio, la base no devuelve filas de
//otro (defensa en profundidad).
//Sin negocio en el contexto se fija vacío: la política no deja ver ninguna
//fila

@Configuration(proxyBeanMethods = false)
public class TenantDataSourceConfig {

    @Bean
    static BeanPostProcessor envolverDataSourceConTenant() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String nombre) {
                if (bean instanceof DataSource ds && !(bean instanceof TenantAwareDataSource)) {
                    return new TenantAwareDataSource(ds);
                }
                return bean;
            }
        };
    }

    static final class TenantAwareDataSource extends DelegatingDataSource {

        TenantAwareDataSource(DataSource destino) {
            super(destino);
        }

        @Override
        public Connection getConnection() throws SQLException {
            return fijarTenant(super.getConnection());
        }

        @Override
        public Connection getConnection(String usuario, String clave) throws SQLException {
            return fijarTenant(super.getConnection(usuario, clave));
        }

        private static Connection fijarTenant(Connection conexion) throws SQLException {
            String negocio = TenantContext.actual().map(t -> t.negocioId().toString()).orElse("");
            try (PreparedStatement ps = conexion.prepareStatement("SELECT set_config('app.negocio_id', ?, false)")) {
                ps.setString(1, negocio);
                ps.execute();
            } catch (SQLException e) {
                conexion.close();
                throw e;
            }
            return conexion;
        }
    }
}
