package com.carloscuatin.ledgerapp;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.jspecify.annotations.NullMarked;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

/**
 * The domain is plain Java; use cases may only borrow Spring's transaction boundary and the JSpecify nullness
 * annotations. Rules allow empty layers because a module does not need to have every layer.
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
            .resideOutsideOfPackages(
                    "java..", "org.springframework.transaction.annotation..", "org.jspecify.annotations..",
                    "com.carloscuatin..")
            .orShould()
            .dependOnClassesThat()
            .resideInAPackage("..adapters..")
            .allowEmptyShould(true);

    /**
     * NullAway only checks packages marked {@code @NullMarked}, and the annotation does not reach subpackages, so a
     * package that forgets its {@code package-info.java} would silently leave the check.
     */
    @ArchTest
    static final ArchRule mainCodeIsNullMarked = classes()
            .that()
            .resideInAPackage("com.carloscuatin.ledgerapp..")
            .should(resideInANullMarkedPackage());

    private static ArchCondition<JavaClass> resideInANullMarkedPackage() {
        return new ArchCondition<>("reside in a package annotated with @NullMarked") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                var satisfied = javaClass.getPackage().isAnnotatedWith(NullMarked.class);
                var message = "%s resides in %s, which has no @NullMarked package-info.java"
                        .formatted(javaClass.getName(), javaClass.getPackageName());
                events.add(new SimpleConditionEvent(javaClass, satisfied, message));
            }
        };
    }
}
