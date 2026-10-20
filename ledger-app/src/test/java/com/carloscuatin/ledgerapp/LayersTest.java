package com.carloscuatin.ledgerapp;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * The domain is plain Java; use cases may only borrow Spring's transaction boundary. Rules allow
 * empty layers because a module does not need to have every layer.
 */
@AnalyzeClasses(packages = "com.carloscuatin.ledgerapp", importOptions = ImportOption.DoNotIncludeTests.class)
class LayersTest {

    @ArchTest
    static final ArchRule domainIsFrameworkFree = noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.springframework..", "jakarta.persistence..", "..application..", "..adapters..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule useCasesOnlyUseTransactions = noClasses()
            .that()
            .resideInAPackage("..application..")
            .should()
            .dependOnClassesThat()
            .resideOutsideOfPackages("java..", "org.springframework.transaction.annotation..", "com.carloscuatin..")
            .orShould()
            .dependOnClassesThat()
            .resideInAPackage("..adapters..")
            .allowEmptyShould(true);
}
