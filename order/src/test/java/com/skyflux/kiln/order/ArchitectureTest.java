package com.skyflux.kiln.order;

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
 * Hexagonal / Clean Architecture hard guards for the {@code order} module.
 */
@AnalyzeClasses(
        packages = "com.skyflux.kiln.order",
        importOptions = {ImportOption.DoNotIncludeTests.class}
)
class ArchitectureTest {

    @ArchTest
    static final ArchRule domain_must_not_depend_on_frameworks =
            noClasses()
                    .that().resideInAPackage("..domain..")
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
                    .resideInAPackage("..adapter..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule no_cyclic_package_dependencies =
            slices()
                    .matching("com.skyflux.kiln.order.(*)..")
                    .should().beFreeOfCycles();

    @ArchTest
    static final ArchRule port_packages_contain_only_interfaces_or_records =
            classes()
                    .that().resideInAPackage("..application.port..")
                    .and().doNotHaveSimpleName("package-info")
                    .should().beInterfaces()
                    .orShould().beRecords()
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule usecase_impls_must_be_named_service =
            classes()
                    .that().resideInAPackage("..application.usecase..")
                    .and().areNotInterfaces()
                    .and().areNotRecords()
                    .and().doNotHaveSimpleName("package-info")
                    .and().areNotAssignableTo(Throwable.class)
                    .should().haveSimpleNameEndingWith("Service")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule domain_must_not_import_jooq_generated =
            noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("com.skyflux.kiln.infra.jooq.generated..");

    @ArchTest
    static final ArchRule no_class_should_depend_on_apache_commons =
            noClasses()
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "org.apache.commons.lang..",
                            "org.apache.commons.lang3..",
                            "org.apache.commons.io..",
                            "org.apache.commons.collections..");

    @ArchTest
    static final ArchRule domain_events_should_be_sealed_or_record =
            classes()
                    .that().resideInAPackage("..domain.event..")
                    .and().areNotAnonymousClasses()
                    .and().doNotHaveSimpleName("package-info")
                    .should(beSealedInterfaceOrRecord());

    @ArchTest
    static final ArchRule inbound_adapters_must_not_use_outbound_ports =
            noClasses()
                    .that().resideInAPackage("..adapter.in..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("..application.port.out..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule persistence_adapters_should_be_package_private =
            classes()
                    .that().resideInAPackage("..adapter.out.persistence..")
                    .and().areNotInterfaces()
                    .and().doNotHaveSimpleName("package-info")
                    .and().haveSimpleNameEndingWith("Adapter")
                    .should().bePackagePrivate()
                    .allowEmptyShould(true);

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
