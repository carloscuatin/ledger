package com.carloscuatin.ledgerapp;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTest {

    @Test
    void modulesRespectTheirBoundaries() {
        ApplicationModules.of(LedgerApplication.class).verify();
    }
}
