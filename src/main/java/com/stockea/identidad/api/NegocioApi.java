package com.stockea.identidad.api;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public interface NegocioApi {

    // Tope de descuento que el negocio permite
    BigDecimal porcentajeMaximoDescuento(UUID negocioId);

    boolean esUsuarioDelNegocio(UUID negocioId, UUID usuarioId);

    // id -> nombre, para mostrar quién hizo qué en los reportes
    Map<UUID, String> nombres(UUID negocioId, Collection<UUID> usuarios);
}
