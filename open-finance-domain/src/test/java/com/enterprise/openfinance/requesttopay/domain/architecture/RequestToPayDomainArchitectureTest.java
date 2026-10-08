package com.enterprise.openfinance.requesttopay.domain.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Dependency rule of the hexagon: the request-to-pay domain depends on the
 * JDK only.
 */
class RequestToPayDomainArchitectureTest {

    private static final JavaClasses DOMAIN = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.enterprise.openfinance.requesttopay.domain");

    @Test
    void domainDoesNotDependOnApplicationOrInfrastructure() {
        noClasses().that().resideInAPackage("com.enterprise.openfinance.requesttopay.domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.enterprise.openfinance.requesttopay.application..",
                        "com.enterprise.openfinance.requesttopay.infrastructure..")
                .check(DOMAIN);
    }

    @Test
    void domainIsFreeOfFrameworkPersistenceAndMessagingTypes() {
        noClasses().that().resideInAPackage("com.enterprise.openfinance.requesttopay.domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..", "jakarta.persistence..", "org.hibernate..",
                        "com.fasterxml.jackson..", "org.apache.kafka..", "com.mongodb..", "lombok..")
                .check(DOMAIN);
    }
}
