package com.enterprise.openfinance.requesttopay.infrastructure.integration;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
@SpringBootTest(
        classes = PayRequestApiIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "spring.autoconfigure.exclude=org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration"
)
@AutoConfigureMockMvc(addFilters = false)
class PayRequestApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldCreateAndFetchPayRequestStatus() throws Exception {
        String payload = """
                {
                  "Data": {
                    "PsuId": "PSU-001",
                    "CreditorName": "Utilities Co",
                    "InstructedAmount": {
                      "Amount": "500.00",
                      "Currency": "AED"
                    }
                  }
                }
                """;

        MvcResult create = mockMvc.perform(withHeaders(post("/open-finance/v1/par")
                        .contentType("application/json")
                        .content(payload)))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-OF-Cache", "MISS"))
                .andExpect(jsonPath("$.Data.ConsentId").exists())
                .andReturn();

        String consentId = JsonPathHelper.read(create.getResponse().getContentAsString(), "$.Data.ConsentId");

        MvcResult statusResponse = mockMvc.perform(withHeaders(get("/open-finance/v1/payment-consents/{consentId}", consentId)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-OF-Cache", "MISS"))
                .andExpect(jsonPath("$.Data.Status").value("AwaitingAuthorisation"))
                .andReturn();

        String etag = statusResponse.getResponse().getHeader("ETag");

        mockMvc.perform(withHeaders(get("/open-finance/v1/payment-consents/{consentId}", consentId)
                        .header("If-None-Match", etag)))
                .andExpect(status().isNotModified());
    }

    @Test
    void shouldRejectInvalidAuthorizationAndDuplicateFinalize() throws Exception {
        String payload = """
                {
                  "Data": {
                    "PsuId": "PSU-002",
                    "CreditorName": "Utilities Co",
                    "InstructedAmount": {
                      "Amount": "200.00",
                      "Currency": "AED"
                    }
                  }
                }
                """;

        mockMvc.perform(post("/open-finance/v1/par")
                        .header("Authorization", "Basic invalid")
                        .header("DPoP", "proof")
                        .header("X-FAPI-Interaction-ID", "ix-request-to-pay-int-err")
                        .header("x-fapi-financial-id", "TPP-001")
                        .contentType("application/json")
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        MvcResult created = mockMvc.perform(withHeaders(post("/open-finance/v1/par")
                        .contentType("application/json")
                        .content(payload)))
                .andExpect(status().isCreated())
                .andReturn();

        String consentId = JsonPathHelper.read(created.getResponse().getContentAsString(), "$.Data.ConsentId");

        mockMvc.perform(withHeaders(post("/open-finance/v1/payment-consents/{consentId}/accept", consentId)
                        .contentType("application/json")
                        .content("{\"paymentId\":\"PAY-123\"}")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.Data.Status").value("Consumed"));

        mockMvc.perform(withHeaders(post("/open-finance/v1/payment-consents/{consentId}/accept", consentId)
                        .contentType("application/json")
                        .content("{\"paymentId\":\"PAY-124\"}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_FINALIZED"))
                .andExpect(jsonPath("$.message", Matchers.containsString("finalized")));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            SecurityAutoConfiguration.class,
            OAuth2ResourceServerAutoConfiguration.class,
            DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            FlywayAutoConfiguration.class,
            MongoAutoConfiguration.class,
            MongoDataAutoConfiguration.class,
            RedisAutoConfiguration.class,
            RedisRepositoriesAutoConfiguration.class
    })
    @ComponentScan(basePackages = {
            "com.enterprise.openfinance.requesttopay.application",
            "com.enterprise.openfinance.requesttopay.infrastructure"
    })
    static class TestApplication {
    }

    private static MockHttpServletRequestBuilder withHeaders(MockHttpServletRequestBuilder builder) {
        return builder
                .header("Authorization", "DPoP demo-token")
                .header("DPoP", "proof")
                .header("X-FAPI-Interaction-ID", "ix-request-to-pay-int")
                .header("x-fapi-financial-id", "TPP-001")
                .accept("application/json");
    }
}
