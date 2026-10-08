package com.enterprise.openfinance.requesttopay.infrastructure.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ADR-028 rules 3 and 4 over all request-to-pay classes: inbound adapters
 * drive the domain's in-ports, and every out-port implementation is an adapter
 * in infrastructure.
 */
class RequestToPayAdapterArchitectureTest {

    private static final String ROOT = "com.enterprise.openfinance.requesttopay";
    private static final JavaClasses ALL = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(ROOT);

    @Test
    void controllersAndListenersDependOnInPortsNotOnApplicationClasses() {
        noClasses().that().areAnnotatedWith(RestController.class)
                .or().areAnnotatedWith(Controller.class)
                .or().containAnyMethodsThat(com.tngtech.archunit.base.DescribedPredicate.describe(
                        "are Kafka listeners", m -> m.isAnnotatedWith(KafkaListener.class)))
                .should().dependOnClassesThat().resideInAPackage(ROOT + ".application..")
                .allowEmptyShould(false)
                .check(ALL);
    }

    @Test
    void outPortImplementationsResideInInfrastructure() {
        classes().that().implement(com.tngtech.archunit.base.DescribedPredicate.describe(
                        "an interface in domain.port.out",
                        (JavaClass type) -> type.getPackageName().equals(ROOT + ".domain.port.out")))
                .and().areNotInterfaces()
                .should().resideInAPackage(ROOT + ".infrastructure..")
                .check(ALL);
    }

    @Test
    void outPortsHaveAtLeastOneAdapter() {
        classes().that().resideInAPackage(ROOT + ".domain.port.out").and().areInterfaces()
                .should(new ArchCondition<>("be implemented by an infrastructure adapter") {
                    @Override
                    public void check(JavaClass port, ConditionEvents events) {
                        boolean implemented = port.getAllSubclasses().stream()
                                .anyMatch(c -> c.getPackageName().startsWith(ROOT + ".infrastructure"));
                        if (!implemented) {
                            events.add(SimpleConditionEvent.violated(port, port.getName() + " has no adapter"));
                        }
                    }
                })
                .check(ALL);
    }
}
