package com.enterprise.openfinance.requesttopay.infrastructure.rest;

import com.enterprise.openfinance.requesttopay.domain.port.in.PayRequestUseCase;
import com.enterprise.openfinance.requesttopay.domain.query.GetPayRequestStatusQuery;
import com.enterprise.openfinance.requesttopay.infrastructure.cache.IdempotencyKeyRepository;
import com.enterprise.openfinance.requesttopay.infrastructure.rest.dto.PayRequestDecisionRequest;
import com.enterprise.openfinance.requesttopay.infrastructure.rest.dto.PayRequestRequest;
import com.enterprise.openfinance.requesttopay.infrastructure.rest.dto.PayRequestResponse;
import com.enterprise.openfinance.requesttopay.infrastructure.rest.dto.PayRequestStatusResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

@RestController
@Validated
@RequestMapping("/api/v1/pay-requests")
public class PayRequestController {

    private final PayRequestUseCase useCase;
    private final IdempotencyKeyRepository idempotencyKeyRepository;

    public PayRequestController(PayRequestUseCase useCase, IdempotencyKeyRepository idempotencyKeyRepository) {
        this.useCase = useCase;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
    }

    @PostMapping
    @DPoPSecured
    @FAPISecured
    public ResponseEntity<?> createPayRequest(
            @RequestHeader("X-FAPI-Interaction-ID") @NotBlank String interactionId,
            @RequestHeader("X-Idempotency-Key") @NotBlank String idempotencyKey,
            @RequestHeader(value = "x-fapi-financial-id", required = false) String financialId,
            @RequestBody @Valid PayRequestRequest request
    ) {
        String payloadHash = generateEtag(request.toString());
        boolean isNewRequest = idempotencyKeyRepository.saveIfAbsent(idempotencyKey, payloadHash, 86400); // 24h TTL
        if (!isNewRequest) {
             return ResponseEntity.status(HttpStatus.CONFLICT).body("Duplicate request detected via Idempotency Key");
        }

        String tppId = resolveTppId(financialId);
        var result = useCase.createPayRequest(request.toCommand(tppId, interactionId));
        String self = "/api/v1/pay-requests/" + result.request().consentId();

        return ResponseEntity.created(java.net.URI.create(self))
                .cacheControl(CacheControl.maxAge(0, TimeUnit.SECONDS).noStore())
                .header("X-FAPI-Interaction-ID", interactionId)
                .header("X-OF-Cache", result.cacheHit() ? "HIT" : "MISS")
                .body(PayRequestResponse.from(result, self));
    }

    @GetMapping("/{consentId}")
    @DPoPSecured
    @FAPISecured
    public ResponseEntity<PayRequestStatusResponse> getPayRequestStatus(
            @RequestHeader("X-FAPI-Interaction-ID") @NotBlank String interactionId,
            @RequestHeader(value = "x-fapi-financial-id", required = false) String financialId,
            @PathVariable @NotBlank String consentId,
            @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch
    ) {
        String tppId = resolveTppId(financialId);

        var result = useCase.getPayRequestStatus(new GetPayRequestStatusQuery(consentId, tppId, interactionId));
        String self = "/api/v1/pay-requests/" + consentId;
        PayRequestStatusResponse response = PayRequestStatusResponse.from(result, self);
        String etag = generateEtag(response.data().toString());

        if (ifNoneMatch != null && ifNoneMatch.equals(etag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .cacheControl(CacheControl.maxAge(0, TimeUnit.SECONDS).noStore())
                    .header("X-FAPI-Interaction-ID", interactionId)
                    .eTag(etag)
                    .build();
        }

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(0, TimeUnit.SECONDS).noStore())
                .header("X-FAPI-Interaction-ID", interactionId)
                .header("X-OF-Cache", result.cacheHit() ? "HIT" : "MISS")
                .eTag(etag)
                .body(response);
    }

    @PostMapping("/{consentId}/accept")
    @DPoPSecured
    @FAPISecured
    public ResponseEntity<PayRequestStatusResponse> acceptPayRequest(
            @RequestHeader("X-FAPI-Interaction-ID") @NotBlank String interactionId,
            @RequestHeader(value = "x-fapi-financial-id", required = false) String financialId,
            @PathVariable @NotBlank String consentId,
            @RequestBody PayRequestDecisionRequest decision
    ) {
        String tppId = resolveTppId(financialId);

        String paymentId = decision == null ? null : decision.paymentId();
        var result = useCase.acceptPayRequest(consentId, tppId, paymentId, interactionId);
        String self = "/api/v1/pay-requests/" + consentId;

        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.maxAge(0, TimeUnit.SECONDS).noStore())
                .header("X-FAPI-Interaction-ID", interactionId)
                .body(PayRequestStatusResponse.from(result, self));
    }

    @PostMapping("/{consentId}/reject")
    @DPoPSecured
    @FAPISecured
    public ResponseEntity<PayRequestStatusResponse> rejectPayRequest(
            @RequestHeader("X-FAPI-Interaction-ID") @NotBlank String interactionId,
            @RequestHeader(value = "x-fapi-financial-id", required = false) String financialId,
            @PathVariable @NotBlank String consentId,
            @RequestBody PayRequestDecisionRequest decision
    ) {
        String tppId = resolveTppId(financialId);

        var result = useCase.rejectPayRequest(consentId, tppId, interactionId);
        String self = "/api/v1/pay-requests/" + consentId;

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(0, TimeUnit.SECONDS).noStore())
                .header("X-FAPI-Interaction-ID", interactionId)
                .body(PayRequestStatusResponse.from(result, self));
    }

    private static String resolveTppId(String financialId) {
        if (financialId == null || financialId.isBlank()) {
            return "UNKNOWN_TPP";
        }
        return financialId.trim();
    }

    private static String generateEtag(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            return '"' + Base64.getUrlEncoder().withoutPadding().encodeToString(hash) + '"';
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Unable to generate ETag", exception);
        }
    }
}
