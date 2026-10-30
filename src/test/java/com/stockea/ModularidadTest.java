package com.stockea;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

class ModularidadTest {

    ApplicationModules modules = ApplicationModules.of(StockeaApplication.class);

    @Test
    void verificaReglasDeModulith() {
        modules.verify();
    }

    @Test
    void generaDiagramasDeComponentes() {
        new Documenter(modules).writeModulesAsPlantUml();
    }
}