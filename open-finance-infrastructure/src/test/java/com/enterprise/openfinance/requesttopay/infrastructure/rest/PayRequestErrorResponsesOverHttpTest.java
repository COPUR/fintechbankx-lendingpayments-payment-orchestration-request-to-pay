package com.enterprise.openfinance.requesttopay.infrastructure.rest;

import com.enterprise.openfinance.requesttopay.domain.port.in.PayRequestUseCase;
import com.enterprise.openfinance.requesttopay.infrastructure.security.DPoPNonceRepository;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Real HTTP through the embedded server, so errors the container turns into an
 * ERROR dispatch to /error behave as they will in the pod. A correctly
 * authenticated TPP must see the real 4xx, never 401/403 from the security
 * chain guarding /error.
 */
@SpringBootTest(
        classes = PayRequestControllerDPoPIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.autoconfigure.exclude=org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration"
)
class PayRequestErrorResponsesOverHttpTest {

    @LocalServerPort
    private int port;

    @MockBean
    private PayRequestUseCase payRequestUseCase;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private DPoPNonceRepository dpopNonceRepository;

    private final HttpClient http = HttpClient.newHttpClient();
    private ECKey dpopKey;

    @BeforeEach
    void setUp() throws Exception {
        dpopKey = new ECKeyGenerator(Curve.P_256).generate();
        when(dpopNonceRepository.saveJtiIfAbsent(anyString(), anyLong())).thenReturn(true);
        when(jwtDecoder.decode(any())).thenReturn(DPoPTestUtils.createJwtWithCnf(dpopKey, "TPP-001"));
    }

    @Test
    void malformedJsonIsBadRequest() throws Exception {
        HttpResponse<String> response = send(HttpMethod.POST, "/open-finance/v1/par", "application/json", "{\"Data\": ");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("INVALID_REQUEST");
    }

    @Test
    void unsupportedMediaTypeIs415() throws Exception {
        HttpResponse<String> response = send(HttpMethod.POST, "/open-finance/v1/par", "text/plain", "hello");

        assertThat(response.statusCode()).isEqualTo(415);
    }

    @Test
    void unsupportedMethodIs405() throws Exception {
        HttpResponse<String> response = send(HttpMethod.DELETE, "/open-finance/v1/payment-consents/c-1", null, null);

        assertThat(response.statusCode()).isEqualTo(405);
    }

    @Test
    void unknownPathUnderTheTppPrefixIs404() throws Exception {
        HttpResponse<String> response = send(HttpMethod.GET, "/open-finance/v1/payment-consents/c-1/unknown", null, null);

        assertThat(response.statusCode()).isEqualTo(404);
    }

    @Test
    void anIllegalStateIsAnInternalErrorWithAFixedMessage() throws Exception {
        when(payRequestUseCase.getPayRequestStatus(any()))
                .thenThrow(new IllegalStateException("Idempotency key neither reserved nor found for CONS-RTP-secret"));

        HttpResponse<String> response = send(HttpMethod.GET, "/open-finance/v1/payment-consents/c-1", null, null);

        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.body()).contains("INTERNAL_ERROR").contains("Unexpected error occurred")
                .doesNotContain("CONS-RTP-secret").doesNotContain("Idempotency key");
    }

    private HttpResponse<String> send(HttpMethod method, String path, String contentType, String body)
            throws Exception {
        String url = "http://localhost:" + port + path;
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
                .header("Authorization", "DPoP token")
                .header("DPoP", DPoPTestUtils.createDPoPProof(dpopKey, method, url))
                .header("X-FAPI-Interaction-ID", "ix-error")
                .header("X-Idempotency-Key", "idem-error")
                .method(method.name(), body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body));
        if (contentType != null) {
            request.header("Content-Type", contentType);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
