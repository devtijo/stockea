package com.stockea.shared.domain;

public enum ErrorDominio {
    CANTIDAD_INVALIDA("La cantidad a asignar debe ser mayor que cero"),
    LOTE_ID_OBLIGATORIO("El identificador del lote es obligatorio"),
    CANTIDAD_FRACCION_INVALIDA("La cantidad de la fracción debe ser mayor a cero"),
    CANTIDAD_DISPONIBLE_NEGATIVA("La cantidad disponible no puede ser negativa"),
    SIN_COBERTURA_NEGATIVA("El valor sin cobertura no puede ser negativo"),
    LOTES_REQUERIDOS("La lista de lotes es obligatoria"),
    COMANDO_VENTA_OBLIGATORIO("El comando de venta es obligatorio"),
    ITEMS_PROCESADOS_OBLIGATORIOS("Los ítems procesados son obligatorios"),
    HORA_SERVIDOR_OBLIGATORIA("La hora del servidor es obligatoria"),
    DISCREPANCIA_RESUELTA("La discrepancia ya fue resuelta"),
    PRECIO_UNITARIO_INVALIDO("El precio unitario no puede ser negativo"),
    PORCENTAJE_DESCUENTO_INVALIDO("El porcentaje de descuento no puede ser negativo"),
    INVARIANTE_ITEM_VENTA_ROTA("La suma de asignaciones más cantidad sin cobertura debe igualar la cantidad vendida"),
    TOTAL_CLIENTE_OBLIGATORIO("El total del cliente es obligatorio"),
    MONTO_RECIBIDO_OBLIGATORIO("El monto recibido es obligatorio"),
    MONTO_RECIBIDO_INSUFICIENTE("El monto recibido no cubre el total de la venta");


    private final String mensaje;

    ErrorDominio(String mensaje) {
        this.mensaje = mensaje;
    }

    public String getMensaje() {
        return mensaje;
    }
}