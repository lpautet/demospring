package net.pautet.softs.demospring;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTest {

    @Test
    void verifiesModuleBoundaries() {
        ApplicationModules.of(DemospringApplication.class).verify();
    }
}
