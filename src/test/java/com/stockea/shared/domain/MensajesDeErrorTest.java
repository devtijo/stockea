package com.stockea.shared.domain;

import com.stockea.caja.domain.ErrorCaja;
import com.stockea.descuentos.domain.ErrorDescuento;
import com.stockea.identidad.domain.ErrorIdentidad;
import com.stockea.inventario.domain.ErrorInventario;
import com.stockea.sync.domain.ErrorSync;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class MensajesDeErrorTest {

    @Test
    @DisplayName("Cada código de error de cada módulo tiene un mensaje y los códigos no se repiten")
    void todosLosCodigosTienenMensaje() {
        List<String> codigos = new ArrayList<>();
        Stream.of(ErrorInventario.values(), ErrorDescuento.values(), ErrorCaja.values(), ErrorSync.values())
                .flatMap(Stream::of)
                .forEach(e -> {
                    assertThat(e.mensaje()).as(e.name()).isNotBlank();
                    assertThat(e.tipo()).as(e.name()).isNotNull();
                    codigos.add(e.name());
                });
        Stream.of(ErrorDominio.values()).forEach(e -> {
            assertThat(e.getMensaje()).as(e.name()).isNotBlank();
            codigos.add(e.name());
        });
        Stream.of(ErrorIdentidad.values()).forEach(e -> {
            assertThat(e.mensaje()).as(e.name()).isNotBlank();
            codigos.add(e.name());
        });
        assertThat(codigos).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("El mensaje se completa con los datos de la regla (por ejemplo el tope de descuento)")
    void mensajeConArgumentos() {
        var e = new ReglaNegocioException(ErrorDescuento.PORCENTAJE_FUERA_DE_RANGO, "30");
        assertThat(e.getMessage()).isEqualTo("El descuento debe estar entre 0 y 30 %");
        assertThat(e.error()).isEqualTo(ErrorDescuento.PORCENTAJE_FUERA_DE_RANGO);
    }
}
