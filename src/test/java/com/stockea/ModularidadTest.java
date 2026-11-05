package com.stockea;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

class ModularidadTest {

    ApplicationModules modules = ApplicationModules.of(StockeaApplication.class);

    @Test
    void verificaReglasDeModulith() {
        assertThatCode(modules::verify).doesNotThrowAnyException();
    }

    @Test
    void generaDiagramasDeComponentes() {
        var documentador = new Documenter(modules);
        documentador.writeModulesAsPlantUml();
        assertThat(java.nio.file.Path.of("target", "spring-modulith-docs", "components.puml")).exists();
    }
}