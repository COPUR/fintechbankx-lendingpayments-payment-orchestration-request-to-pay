package com.enterprise.openfinance.domain.event;

import com.enterprise.openfinance.domain.model.consent.ConsentId;
import com.enterprise.openfinance.domain.model.consent.ConsentScope;
import com.enterprise.openfinance.domain.model.participant.ParticipantId;
import com.enterprise.shared.domain.CustomerId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DomainEventsCoverageTest {

    @Test
    void testDataSharingRequest() {
        DataSharingRequest request = DataSharingRequest.builder()
                .requestId("req-1")
                .consentId(ConsentId.generate())
                .participantId(ParticipantId.generate())
                .customerId(CustomerId.generate())
                .requestedScopes(Set.of(ConsentScope.LOAN_INFORMATION))
                .dataSize(100L)
                .dataFormat("JSON")
                .complianceRequirements("PCI")
                .encryptionMethod("RSA")
                .participantPublicKey("pub-key")
                .deliveryEndpoint("https://delivery.com")
                .deliveryMethod("PUSH")
                .callbackUrl("https://callback.com")
                .build();

        assertThat(request.getRequestId()).isEqualTo("req-1");
        assertThat(request.toString()).isNotBlank();
        assertThat(request.hashCode()).isNotZero();
        assertThat(request.equals(request)).isTrue();
    }

    @Test
    void testDataSharingResult() {
        DataSharingResult result = DataSharingResult.builder()
                .sagaId("saga-1")
                .requestId("req-1")
                .consentId(ConsentId.generate())
                .customerId(CustomerId.generate())
                .participantId(ParticipantId.generate())
                .status(DataSharingStatus.COMPLETED)
                .failureReason("none")
                .failedAt(Instant.now())
                .sharedAt(Instant.now())
                .dataSources(List.of("SRC1"))
                .dataSize(100L)
                .encryptionMethod("RSA")
                .deliveryMethod("PUSH")
                .executionTimeMs(50L)
                .build();

        assertThat(result.getSagaId()).isEqualTo("saga-1");
        assertThat(result.toString()).isNotBlank();
        assertThat(result.hashCode()).isNotZero();
        assertThat(result.equals(result)).isTrue();
    }

    @Test
    void testPlatformData() {
        PlatformData data = PlatformData.builder()
                .sourcePlatform("Platform A")
                .dataSize(100L)
                .payload("payload")
                .build();

        assertThat(data.getSourcePlatform()).isEqualTo("Platform A");
        assertThat(data.toString()).isNotBlank();
        assertThat(data.hashCode()).isNotZero();
        assertThat(data.equals(data)).isTrue();
    }

    @Test
    void testAggregatedData() {
        AggregatedData data = AggregatedData.builder()
                .aggregationId("agg-1")
                .platformDataList(List.of())
                .sourceCount(1)
                .dataSize(100L)
                .dataSources(List.of("SRC1"))
                .aggregatedAt(Instant.now())
                .build();

        assertThat(data.getAggregationId()).isEqualTo("agg-1");
        assertThat(data.toString()).isNotBlank();
        assertThat(data.hashCode()).isNotZero();
        assertThat(data.equals(data)).isTrue();
    }

    @Test
    void testTransformedData() {
        TransformedData data = TransformedData.builder()
                .transformationId("trans-1")
                .transformedPayload("payload")
                .build();

        assertThat(data.getTransformationId()).isEqualTo("trans-1");
        assertThat(data.toString()).isNotBlank();
        assertThat(data.hashCode()).isNotZero();
        assertThat(data.equals(data)).isTrue();
    }

    @Test
    void testEncryptedData() {
        EncryptedData data = EncryptedData.builder()
                .encryptionId("enc-1")
                .encryptedPayload(new byte[]{1, 2, 3})
                .build();

        assertThat(data.getEncryptionId()).isEqualTo("enc-1");
        assertThat(data.toString()).isNotBlank();
        assertThat(data.hashCode()).isNotZero();
        assertThat(data.equals(data)).isTrue();
    }

    @Test
    void testDataDeliveryResult() {
        DataDeliveryResult result = DataDeliveryResult.builder()
                .success(true)
                .errorMessage("none")
                .errorCode("200")
                .deliveryTimestamp(Instant.now())
                .build();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.toString()).isNotBlank();
        assertThat(result.hashCode()).isNotZero();
        assertThat(result.equals(result)).isTrue();
    }

    @Test
    void testConsentAuthorizationRequest() {
        ConsentAuthorizationRequest request = ConsentAuthorizationRequest.builder()
                .consentId(ConsentId.generate())
                .participantId(ParticipantId.generate())
                .customerId(CustomerId.generate())
                .certificates(List.of("cert-1"))
                .requestSignature("sig")
                .scopes(Set.of(ConsentScope.LOAN_INFORMATION))
                .purpose("purpose")
                .expirationDate(Instant.now())
                .authorizationUrl("url")
                .build();

        assertThat(request.getPurpose()).isEqualTo("purpose");
        assertThat(request.toString()).isNotBlank();
        assertThat(request.hashCode()).isNotZero();
        assertThat(request.equals(request)).isTrue();
    }

    @Test
    void testConsentAuthorizationResult() {
        ConsentAuthorizationResult result = ConsentAuthorizationResult.builder()
                .sagaId("saga-1")
                .consentId(ConsentId.generate())
                .customerId(CustomerId.generate())
                .participantId(ParticipantId.generate())
                .status(ConsentAuthorizationStatus.AUTHORIZED)
                .failureReason("none")
                .failedAt(Instant.now())
                .authorizedAt(Instant.now())
                .completedSteps(List.of("step-1"))
                .executionTimeMs(100L)
                .build();

        assertThat(result.getSagaId()).isEqualTo("saga-1");
        assertThat(result.toString()).isNotBlank();
        assertThat(result.hashCode()).isNotZero();
        assertThat(result.equals(result)).isTrue();
    }

    @Test
    void testCustomerAuthorizationResult() {
        CustomerAuthorizationResult result1 = CustomerAuthorizationResult.authorized(ConsentId.generate(), Instant.now());
        assertThat(result1.isAuthorized()).isTrue();
        assertThat(result1.isTimeout()).isFalse();

        CustomerAuthorizationResult result2 = CustomerAuthorizationResult.timeout(ConsentId.generate());
        assertThat(result2.isAuthorized()).isFalse();
        assertThat(result2.isTimeout()).isTrue();

        assertThat(result1.toString()).isNotBlank();
        assertThat(result1.hashCode()).isNotZero();
        assertThat(result1.equals(result1)).isTrue();
    }

    @Test
    void testConsentValidationResult() {
        ConsentValidationResult result = ConsentValidationResult.builder()
                .valid(true)
                .violations(Set.of())
                .build();

        assertThat(result.isValid()).isTrue();
        assertThat(result.toString()).isNotBlank();
        assertThat(result.hashCode()).isNotZero();
        assertThat(result.equals(result)).isTrue();
    }

    @Test
    void testParticipantValidationResult() {
        ParticipantValidationResult result = ParticipantValidationResult.builder()
                .success(true)
                .errorMessage("none")
                .errorCode("200")
                .build();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.toString()).isNotBlank();
        assertThat(result.hashCode()).isNotZero();
        assertThat(result.equals(result)).isTrue();
    }

    @Test
    void testCBUAERegistrationResult() {
        CBUAERegistrationResult result = CBUAERegistrationResult.builder()
                .success(true)
                .errorMessage("none")
                .errorCode("200")
                .build();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.toString()).isNotBlank();
        assertThat(result.hashCode()).isNotZero();
        assertThat(result.equals(result)).isTrue();
    }

    @Test
    void testRateLimitResult() {
        RateLimitResult result = RateLimitResult.builder()
                .allowed(true)
                .errorMessage("none")
                .errorCode("200")
                .build();

        assertThat(result.isAllowed()).isTrue();
        assertThat(result.toString()).isNotBlank();
        assertThat(result.hashCode()).isNotZero();
        assertThat(result.equals(result)).isTrue();
    }
}
