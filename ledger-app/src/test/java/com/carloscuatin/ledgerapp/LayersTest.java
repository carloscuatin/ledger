package com.carloscuatin.ledgerapp;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import java.lang.annotation.Annotation;
import java.util.List;

import org.jspecify.annotations.NullMarked;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.service.annotation.HttpExchange;

import com.carloscuatin.ledgerapp.ledger.adapters.in.web.TenantPrincipal;
import com.carloscuatin.ledgerapp.ledger.application.CurrentTenant;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.properties.HasAnnotations;
import com.tngtech.archunit.core.domain.properties.HasName;
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

    private static final String GENERATED_INTERFACES = "com.carloscuatin.ledger.contract..api..";

    private static final String ERROR_PAGE_CONTROLLER = "com.carloscuatin.ledgerapp.ledger.adapters.in.web.ContainerErrorController";

    /** Spring MVC maps both: {@code @GetMapping} and its siblings carry the first, {@code @GetExchange} the second. */
    private static final List<Class<? extends Annotation>> ROUTE_ANNOTATIONS = List.of(RequestMapping.class,
            HttpExchange.class);

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
     * A tenant comes from outside the application, so only what receives work from outside may open its scope. Tests are
     * not imported here, so they can open one with the same call. Any access counts, a method reference as well as a call.
     */
    @ArchTest
    static final ArchRule onlyInboundAdaptersOpenATenantScope = noClasses()
            .that()
            .resideOutsideOfPackage("..adapters.in..")
            .should()
            .accessTargetWhere(JavaAccess.Predicates.target(HasName.Predicates.name("callAs"))
                    .and(JavaAccess.Predicates.targetOwner(JavaClass.Predicates.type(CurrentTenant.class))));

    /**
     * NullAway only checks packages marked {@code @NullMarked}, and the annotation does not reach subpackages, so a
     * package that forgets its {@code package-info.java} would silently leave the check.
     */
    @ArchTest
    static final ArchRule mainCodeIsNullMarked = classes()
            .that()
            .resideInAPackage("com.carloscuatin.ledgerapp..")
            .should(resideInANullMarkedPackage());

    /**
     * The contract is the source of the API, and an interface generated from it is what ties a controller to the
     * contract: the compiler then refuses a controller that misses one of its operations.
     */
    @ArchTest
    static final ArchRule restControllersImplementAGeneratedInterface = classes()
            .that()
            .areMetaAnnotatedWith(RestController.class)
            .should()
            .implement(JavaClass.Predicates.resideInAPackage(GENERATED_INTERFACES)
                    .as("an interface generated from the contract"));

    /**
     * A route written by hand is one the contract does not document. Controllers inherit their mappings from the
     * generated interfaces, so no mapping annotation appears in their own bytecode. The one exception is the server's
     * error page, which clients do not call and no contract describes; the server forwards to it, and it needs a route
     * for that. It is named exactly, so another class cannot pass as one.
     */
    @ArchTest
    static final ArchRule handWrittenCodeDeclaresNoRoutes = classes()
            .that()
            .doNotHaveFullyQualifiedName(ERROR_PAGE_CONTROLLER)
            .should(declareNoRoutes());

    /**
     * The first transaction of a request belongs to a use case, which has the tenant in scope and knows whether it writes.
     * A use case joins a transaction that is already open and inherits its settings, whatever it declares itself. Spring
     * honours the Jakarta annotation as well as its own, so both packages count.
     */
    @ArchTest
    static final ArchRule inboundAdaptersOpenNoTransaction = noClasses()
            .that()
            .resideInAPackage("..adapters.in..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.springframework.transaction..", "jakarta.transaction..");

    /**
     * A principal carries the tenant whose rows a request may touch. Production has no mechanism that verifies a
     * credential, so a class implementing this would be a way in without one; the mechanism that verifies credentials has
     * to replace this rule knowingly.
     */
    @ArchTest
    static final ArchRule noProductionClassIsAPrincipalWithATenant = noClasses().should()
            .implement(TenantPrincipal.class);

    private static ArchCondition<JavaClass> declareNoRoutes() {
        return new ArchCondition<>("declare no routes") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                report(javaClass, javaClass, events);
                javaClass.getMethods().forEach(method -> report(method, javaClass, events));
            }

            private void report(HasAnnotations<?> element, JavaClass owner, ConditionEvents events) {
                var declaresARoute = ROUTE_ANNOTATIONS.stream().anyMatch(element::isMetaAnnotatedWith);
                var message = "%s declares a route by hand".formatted(element.getDescription());
                events.add(new SimpleConditionEvent(owner, !declaresARoute, message));
            }
        };
    }

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
