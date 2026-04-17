package com.skyflux.kiln;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTest {

    @Test
    void listModules() {
        ApplicationModules modules = ApplicationModules.of(KilnApplication.class);
        modules.forEach(System.out::println);
    }

}
