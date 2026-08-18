package com.swiftbite.analytics;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Enforces the {@code pkg/ -> lib/ -> app/} layering rule from CLAUDE.md §3
 * as a compiled check rather than a code-review convention:
 * <ul>
 *   <li>{@code pkg/} stays framework- and app-agnostic — it may not import
 *       {@code lib/} or {@code app/}.</li>
 *   <li>{@code lib/} may not import {@code app/} directly. Where a lib-level
 *       component needs app-level behavior (e.g. the order-events consumer
 *       dispatching into a rollup service), lib/ defines an interface and
 *       app/ supplies the implementation as a Spring bean — the DI container
 *       wires it at boot, which is this codebase's equivalent of the "DI
 *       tokens at boot" exception CLAUDE.md carves out. app/-to-app/ imports
 *       (e.g. one module's service calling another's) are unrestricted.</li>
 * </ul>
 */
class LayeringTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter().importPackages("com.swiftbite.analytics");
    }

    @Test
    void pkgMustNotDependOnLibOrApp() {
        // allowEmptyShould: no pkg/ package exists yet as of Phase 1 (it
        // lands with the messaging/cache providers in later phases) —
        // without this, ArchUnit treats a rule that matched zero classes as
        // a likely typo and fails it rather than vacuously passing.
        ArchRule rule = noClasses()
                .that().resideInAPackage("..pkg..")
                .should().dependOnClassesThat().resideInAnyPackage("..lib..", "..app..")
                .allowEmptyShould(true);
        rule.check(classes);
    }

    @Test
    void libMustNotDependOnApp() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..lib..")
                .should().dependOnClassesThat().resideInAPackage("..app..");
        rule.check(classes);
    }
}
