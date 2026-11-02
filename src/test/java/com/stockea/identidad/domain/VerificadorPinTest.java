package com.stockea.identidad.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VerificadorPinTest {

    @Test
    void elPinCorrectoCoincideYElIncorrectoNo() {
        var material = VerificadorPin.derivar("4821");
        assertThat(VerificadorPin.coincide("4821", material)).isTrue();
        assertThat(VerificadorPin.coincide("4822", material)).isFalse();
        assertThat(material.iteraciones()).isEqualTo(VerificadorPin.ITERACIONES);
    }

    @Test
    void cadaDerivacionUsaUnaSalDistinta() {
        var uno = VerificadorPin.derivar("4821");
        var dos = VerificadorPin.derivar("4821");
        assertThat(uno.salBase64()).isNotEqualTo(dos.salBase64());
        assertThat(uno.hashBase64()).as("mismo PIN, hash distinto: no se puede comparar entre usuarios")
                .isNotEqualTo(dos.hashBase64());
    }

    @Test
    void rechazaPinesQueNoSonDe4a6Digitos() {
        assertThatThrownBy(() -> VerificadorPin.derivar("123")).isInstanceOf(ReglaIdentidadException.class);
        assertThatThrownBy(() -> VerificadorPin.derivar("1234567")).isInstanceOf(ReglaIdentidadException.class);
        assertThatThrownBy(() -> VerificadorPin.derivar("12a4")).isInstanceOf(ReglaIdentidadException.class);
    }
}
