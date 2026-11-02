package com.stockea.identidad.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentoTest {

    @Test
    void elNombreDeUsuarioSaleDelTipoYNumero() {
        assertThat(new Documento(Documento.TipoDocumento.CC, "1020304050").nombreDeUsuario()).isEqualTo("cc-1020304050");
        assertThat(new Documento(Documento.TipoDocumento.PA, "AB12345").nombreDeUsuario()).isEqualTo("pa-ab12345");
    }

    @Test
    void rechazaNumerosConCaracteresRarosOLongitudInvalida() {
        assertThatThrownBy(() -> new Documento(Documento.TipoDocumento.CC, "123")).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> new Documento(Documento.TipoDocumento.CC, "10.203.040")).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> new Documento(Documento.TipoDocumento.CC, "1' OR '1'='1")).isInstanceOf(RuntimeException.class);
    }
}
