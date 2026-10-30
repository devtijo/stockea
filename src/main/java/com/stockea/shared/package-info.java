/**
 * Módulo compartido: contiene utilidades transversales como excepciones de dominio
 * y contexto de tenant. Es un módulo abierto porque todos los módulos pueden usarlo (ADR-011).
 */
@org.springframework.modulith.ApplicationModule(
        type = org.springframework.modulith.ApplicationModule.Type.OPEN
)
package com.stockea.shared;