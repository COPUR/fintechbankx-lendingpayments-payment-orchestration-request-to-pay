package com.enterprise.openfinance.requesttopay.application.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** ADR-028 rule 2: the application layer depends on the domain, never on adapters. */
class RequestToPayApplicationArchitectureTest {

    private static final JavaClasses APPLICATION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.enterprise.openfinance.requesttopay.application");

    @Test
    void applicationDoesNotDependOnInfrastructure() {
        noClasses().that().resideInAPackage("com.enterprise.openfinance.requesttopay.application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.enterprise.openfinance.requesttopay.infrastructure..",
                        "jakarta.persistence..", "org.springframework.kafka..", "org.apache.kafka..")
                .check(APPLICATION);
    }

    @Test
    void applicationServicesImplementInboundPorts() {
        classes().that().resideInAPackage("com.enterprise.openfinance.requesttopay.application")
                .and().haveSimpleNameEndingWith("Service")
                .should().implement(com.enterprise.openfinance.requesttopay.domain.port.in.PayRequestUseCase.class)
                .check(APPLICATION);
    }
}
