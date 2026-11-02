package com.stockea.identidad.application;

import com.stockea.identidad.api.DispositivoApi;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;

@Service
class DispositivoApiImpl implements DispositivoApi {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    DispositivoApiImpl(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void registrarUso(UUID negocioId, UUID dispositivoId, UUID usuarioId) {
        // RLS: solo se ve el usuario si es de este negocio.
        var activos = jdbc.queryForList("SELECT activo FROM usuario WHERE id = ?", Boolean.class, usuarioId);
        if (activos.isEmpty() || !Boolean.TRUE.equals(activos.getFirst())) {
            throw new AccesoRevocadoException("El usuario fue inhabilitado en este negocio");
        }
        var ahora = Timestamp.from(clock.instant());
        Boolean activo = jdbc.queryForObject("""
                INSERT INTO dispositivo (negocio_id, id, registrado_por, registrado_en, ultimo_uso_en)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (negocio_id, id) DO UPDATE
                   SET ultimo_uso_en = CASE WHEN dispositivo.activo THEN EXCLUDED.ultimo_uso_en
                                            ELSE dispositivo.ultimo_uso_en END
                RETURNING activo""", Boolean.class, negocioId, dispositivoId, usuarioId, ahora, ahora);
        if (!Boolean.TRUE.equals(activo)) {
            throw new AccesoRevocadoException("El dispositivo está bloqueado para este negocio");
        }
    }
}
