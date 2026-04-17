package com.skyflux.kiln.user;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Hexagonal / Clean Architecture hard guards for the {@code user} module
 * (see design.md Ch 19.12 — ArchUnit 硬约束).
 *
 * <p>Rules 1-3: baseline layer-isolation guards.
 * Rules 4-10 (Phase 4.1): stricter Hexagonal guards + drift-prevention checks.
 */
@AnalyzeClasses(
        packages = "com.skyflux.kiln.user",
        importOptions = {ImportOption.DoNotIncludeTests.class}
)
class ArchitectureTest {

    // ─────────────────────────── Rules 1-3: baseline (DO NOT WEAKEN) ────────────────────────────

    @ArchTest
    static final ArchRule domain_must_not_depend_on_frameworks =
            noClasses()
                    .that().resideInAPackage("..domain..")
                    // package-info carries metadata only (e.g. Spring Modulith
                    // @NamedInterface on domain.event); it holds no behaviour
                    // and so does not "pollute" domain code with runtime coupling.
                    .and().doNotHaveSimpleName("package-info")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "org.springframework..",
                            "jakarta.persistence..",
                            "jakarta.servlet..",
                            "org.jooq..",
                            "com.fasterxml.jackson..");

    @ArchTest
    static final ArchRule domain_must_not_depend_on_application =
            noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("..application..");

    @ArchTest
    static final ArchRule application_must_not_depend_on_adapter =
            noClasses()
                    .that().resideInAPackage("..application..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("..adapter..");

    // ─────────────────────────── Rule 4: no cyclic package dependencies ─────────────────────────

    /** If top-level slices (domain / application / adapter / config) form a cycle, layering is broken. */
    @ArchTest
    static final ArchRule no_cyclic_package_dependencies =
            slices()
                    .matching("com.skyflux.kiln.user.(*)..")
                    .should().beFreeOfCycles();

    // ─────────────────────── Rule 5: ports are interfaces or records only ───────────────────────

    /**
     * {@code application.port..} must contain only interfaces or records — never ordinary classes
     * with business logic. Records are legal because ports may declare {@code Command} / {@code Query}
     * value carriers next to the port interface (see {@code RegisterUserUseCase.Command}).
     */
    @ArchTest
    static final ArchRule port_packages_contain_only_interfaces_or_records =
            classes()
                    .that().resideInAPackage("..application.port..")
                    .and().doNotHaveSimpleName("package-info")
                    .should().beInterfaces()
                    .orShould().beRecords();

    // ─────────────────────── Rule 6: use-case impls must be *Service ────────────────────────────

    @ArchTest
    static final ArchRule usecase_impls_must_be_named_service =
            classes()
                    .that().resideInAPackage("..application.usecase..")
                    .and().areNotInterfaces()
                    .and().areNotRecords()
                    .and().doNotHaveSimpleName("package-info")
                    .should().haveSimpleNameEndingWith("Service");

    // ─────────────── Rule 7: domain must not import jOOQ-generated classes ──────────────────────

    /**
     * Redundant with Rule 1's {@code org.jooq..} block at the package-prefix level, but worth pinning
     * explicitly — jOOQ-generated {@code Record}/{@code Pojo} types are a tempting shortcut that
     * would pollute domain with persistence concerns.
     */
    @ArchTest
    static final ArchRule domain_must_not_import_jooq_generated =
            noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("com.skyflux.kiln.infra.jooq.generated..");

    // ─────────────────── Rule 8: Apache Commons is a hard no (prefer JDK / Hutool) ──────────────

    @ArchTest
    static final ArchRule no_class_should_depend_on_apache_commons =
            noClasses()
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "org.apache.commons.lang..",
                            "org.apache.commons.lang3..",
                            "org.apache.commons.io..",
                            "org.apache.commons.collections..");

    // ─────────── Rule 9: domain events must be sealed interfaces or records ─────────────────────

    /**
     * Domain events ({@code ..domain.event..}) must be either sealed interfaces (event family root)
     * or records (concrete events). No ordinary classes, no non-sealed interfaces.
     *
     * <p>ArchUnit 1.3.0's {@code JavaModifier} enum does not (yet) include {@code SEALED}, so we
     * reflect into the underlying {@link Class#isSealed()} (Java 17+ API) via {@link JavaClass#reflect()}.
     */
    @ArchTest
    static final ArchRule domain_events_should_be_sealed_or_record =
            classes()
                    .that().resideInAPackage("..domain.event..")
                    .and().areNotAnonymousClasses()
                    .and().doNotHaveSimpleName("package-info")
                    .should(beSealedInterfaceOrRecord());

    // ── Rule 10.1: inbound web adapters must NOT reach outbound ports (hexagonal) ────────────

    /**
     * Phase 4.2 Gate 3 I3 — the admin endpoint originally wired directly into
     * {@code UserRepository} (an outbound port). That's a layering shortcut
     * the Hexagonal diagram forbids: inbound adapters talk to inbound use-case
     * ports only, and the outbound port is reached through the service layer.
     * Pinning the rule here prevents regressions as new admin endpoints land.
     */
    @ArchTest
    static final ArchRule inbound_adapters_must_not_use_outbound_ports =
            noClasses()
                    .that().resideInAPackage("..adapter.in..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("..application.port.out..");

    // ─── Rule 11: persistence adapters must be package-private (only the port is public API) ────

    @ArchTest
    static final ArchRule persistence_adapters_should_be_package_private =
            classes()
                    .that().resideInAPackage("..adapter.out.persistence..")
                    .and().areNotInterfaces()
                    .and().doNotHaveSimpleName("package-info")
                    .and().haveSimpleNameEndingWith("Adapter")
                    .should().bePackagePrivate();

    // ─────────────────────────────── custom conditions ──────────────────────────────────────────

    private static ArchCondition<JavaClass> beSealedInterfaceOrRecord() {
        return new ArchCondition<>("be a sealed interface or a record") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                boolean isRecord = item.isRecord();
                boolean sealedInterface = false;
                if (item.isInterface()) {
                    try {
                        sealedInterface = item.reflect().isSealed();
                    } catch (Throwable ignored) {
                        // class not loadable from test classpath — leave sealedInterface=false
                    }
                }
                if (!isRecord && !sealedInterface) {
                    events.add(SimpleConditionEvent.violated(
                            item,
                            item.getName() + " is not a sealed interface or a record"));
                }
            }
        };
    }
}
