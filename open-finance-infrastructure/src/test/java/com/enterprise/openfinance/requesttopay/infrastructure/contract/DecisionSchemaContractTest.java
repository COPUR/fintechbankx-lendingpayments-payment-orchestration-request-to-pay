package com.enterprise.openfinance.requesttopay.infrastructure.contract;

import com.enterprise.openfinance.requesttopay.domain.model.valueobject.DecisionBy;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The published Decision schema states the limits the service enforces. */
@Tag("unit")
class DecisionSchemaContractTest {

    private static final Path SPEC = Path.of("..", "api", "openapi", "request-to-pay-service.yaml");

    @Test
    void reasonIsLimitedToWhatTheServiceAccepts() throws IOException {
        assertThat(property("reason")).containsEntry("maxLength", DecisionBy.MAX_REASON);
    }

    @Test
    void paymentIdIsNeverEmpty() throws IOException {
        assertThat(property("paymentId")).containsEntry("minLength", 1);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> property(String name) throws IOException {
        try (Reader reader = Files.newBufferedReader(SPEC)) {
            Map<String, Object> spec = new Yaml().load(reader);
            Map<String, Object> schemas = (Map<String, Object>) ((Map<String, Object>) spec.get("components")).get("schemas");
            Map<String, Object> decision = (Map<String, Object>) schemas.get("Decision");
            return (Map<String, Object>) ((Map<String, Object>) decision.get("properties")).get(name);
        }
    }
}
