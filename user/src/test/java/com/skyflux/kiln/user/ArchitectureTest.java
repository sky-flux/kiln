package com.skyflux.kiln.user;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Hexagonal / Clean Architecture hard guards for the {@code user} module
 * (see design.md Ch 19.12 — ArchUnit 硬约束).
 *
 * <p>Rules:
 * <ul>
 *   <li>domain must not depend on any framework (Spring, JPA, Servlet, jOOQ, Jackson)</li>
 *   <li>domain must not depend on application</li>
 *   <li>application must not depend on adapter</li>
 * </ul>
 */
@AnalyzeClasses(
        packages = "com.skyflux.kiln.user",
        importOptions = {ImportOption.DoNotIncludeTests.class}
)
class ArchitectureTest {

    @ArchTest
    static final ArchRule domain_must_not_depend_on_frameworks =
            noClasses()
                    .that().resideInAPackage("..domain..")
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
}
