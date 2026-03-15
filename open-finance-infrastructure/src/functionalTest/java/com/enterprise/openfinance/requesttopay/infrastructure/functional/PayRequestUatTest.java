package com.enterprise.openfinance.requesttopay.infrastructure.functional;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.ComponentScan;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@Tag("functional")
@Tag("e2e")
@Tag("uat")
@SpringBootTest(
        classes = PayRequestUatTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.autoconfigure.exclude=org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration"
)
class PayRequestUatTest {

    @LocalServerPort
    private int port;

    @BeforeEach
    void setUp() {
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @Test
    void shouldCompletePayRequestLifecycle() {
        Response created = request()
                .contentType("application/json")
                .body("""
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
                        """)
                .when()
                .post("/open-finance/v1/par")
                .then()
                .statusCode(201)
                .body("Data.ConsentId", notNullValue())
                .body("Data.Status", equalTo("AwaitingAuthorisation"))
                .extract()
                .response();

        String consentId = created.path("Data.ConsentId");

        request()
                .when()
                .get("/open-finance/v1/payment-consents/{consentId}", consentId)
                .then()
                .statusCode(200)
                .body("Data.Status", equalTo("AwaitingAuthorisation"));

        request()
                .contentType("application/json")
                .body("{\"paymentId\":\"PAY-123\"}")
                .when()
                .post("/open-finance/v1/payment-consents/{consentId}/accept", consentId)
                .then()
                .statusCode(201)
                .body("Data.Status", equalTo("Consumed"));

        request()
                .contentType("application/json")
                .body("{\"reason\":\"User rejected\"}")
                .when()
                .post("/open-finance/v1/payment-consents/{consentId}/reject", consentId)
                .then()
                .statusCode(400)
                .body("code", equalTo("REQUEST_FINALIZED"));
    }

    @Test
    void shouldRejectInvalidAuthorization() {
        given().baseUri("http://localhost").port(port)
                .contentType("application/json")
                .accept("application/json")
                .header("Authorization", "Basic invalid")
                .header("DPoP", "proof")
                .header("X-FAPI-Interaction-ID", "ix-request-to-pay-uat-err")
                .header("x-fapi-financial-id", "TPP-001")
                .body("{\"Data\":{\"PsuId\":\"PSU-001\",\"CreditorName\":\"Utilities Co\",\"InstructedAmount\":{\"Amount\":\"50.00\",\"Currency\":\"AED\"}}}")
                .when()
                .post("/open-finance/v1/par")
                .then()
                .statusCode(400)
                .body("code", equalTo("INVALID_REQUEST"));
    }

    private RequestSpecification request() {
        return given().baseUri("http://localhost").port(port)
                .accept("application/json")
                .header("Authorization", "DPoP demo-token")
                .header("DPoP", "proof")
                .header("X-FAPI-Interaction-ID", "ix-request-to-pay-uat")
                .header("x-fapi-financial-id", "TPP-001");
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
}
