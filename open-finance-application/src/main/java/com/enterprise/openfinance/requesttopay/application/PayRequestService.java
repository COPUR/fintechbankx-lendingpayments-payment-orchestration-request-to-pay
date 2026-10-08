package com.enterprise.openfinance.requesttopay.application;

import com.enterprise.openfinance.requesttopay.domain.command.CreatePayRequestCommand;
import com.enterprise.openfinance.requesttopay.domain.exception.IdempotencyKeyConflictException;
import com.enterprise.openfinance.requesttopay.domain.exception.PayRequestAccessDeniedException;
import com.enterprise.openfinance.requesttopay.domain.exception.ResourceNotFoundException;
import com.enterprise.openfinance.requesttopay.domain.model.IdempotencyRecord;
import com.enterprise.openfinance.requesttopay.domain.model.PayRequest;
import com.enterprise.openfinance.requesttopay.domain.model.PayRequestResult;
import com.enterprise.openfinance.requesttopay.domain.model.PayRequestSettings;
import com.enterprise.openfinance.requesttopay.domain.model.valueobject.DecisionBy;
import com.enterprise.openfinance.requesttopay.domain.port.in.PayRequestUseCase;
import com.enterprise.openfinance.requesttopay.domain.port.out.PayRequestCachePort;
import com.enterprise.openfinance.requesttopay.domain.port.out.PayRequestEventPublisher;
import com.enterprise.openfinance.requesttopay.domain.port.out.PayRequestIdempotencyPort;
import com.enterprise.openfinance.requesttopay.domain.port.out.PayRequestRepositoryPort;
import com.enterprise.openfinance.requesttopay.domain.query.GetPayRequestStatusQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * Request-to-pay use cases. Each command loads the aggregate, calls one
 * aggregate method, saves it and hands the registered events to the
 * transactional outbox in the same transaction.
 */
@Service
public class PayRequestService implements PayRequestUseCase {

    private final PayRequestRepositoryPort repositoryPort;
    private final PayRequestCachePort cachePort;
    private final PayRequestEventPublisher eventPublisher;
    private final PayRequestIdempotencyPort idempotencyPort;
    private final PayRequestSettings settings;
    private final Clock clock;
    private final Supplier<String> consentIdGenerator;

    public PayRequestService(PayRequestRepositoryPort repositoryPort,
                             PayRequestCachePort cachePort,
                             PayRequestEventPublisher eventPublisher,
                             PayRequestIdempotencyPort idempotencyPort,
                             PayRequestSettings settings,
                             Clock clock,
                             Supplier<String> consentIdGenerator) {
        this.repositoryPort = repositoryPort;
        this.cachePort = cachePort;
        this.eventPublisher = eventPublisher;
        this.idempotencyPort = idempotencyPort;
        this.settings = settings;
        this.clock = clock;
        this.consentIdGenerator = consentIdGenerator;
    }

    @Override
    @Transactional
    public PayRequestResult createPayRequest(CreatePayRequestCommand command) {
        Instant now = Instant.now(clock);
        String consentId = consentIdGenerator.get();

        if (command.idempotencyKey() != null) {
            String fingerprint = command.fingerprint();
            Optional<IdempotencyRecord> earlier = idempotencyPort.reserve(command.tppId(), command.idempotencyKey(),
                    fingerprint, consentId, now, now.plus(settings.idempotencyTtl()));
            if (earlier.isPresent()) {
                return replay(earlier.orElseThrow(), fingerprint);
            }
        }

        PayRequest created = PayRequest.create(consentId, command, now);
        PayRequest saved = repositoryPort.save(created);
        eventPublisher.publish(created, created.domainEvents(), command.interactionId());
        return new PayRequestResult(saved, false);
    }

    @Override
    @Transactional(readOnly = true)
    public PayRequestResult getPayRequestStatus(GetPayRequestStatusQuery query) {
        Instant now = Instant.now(clock);
        String cacheKey = cacheKey(query.consentId(), query.tppId());

        Optional<PayRequestResult> cached = cachePort.getStatus(cacheKey, now);
        if (cached.isPresent()) {
            return cached.orElseThrow().withCacheHit(true);
        }

        PayRequest request = repositoryPort.findByConsentId(query.consentId())
                .orElseThrow(() -> new ResourceNotFoundException("Pay request not found"));

        ensureOwnership(request, query.tppId());

        PayRequestResult result = new PayRequestResult(request, false);
        cachePort.putStatus(cacheKey, result, now.plus(settings.cacheTtl()));
        return result;
    }

    @Override
    @Transactional
    public PayRequestResult acceptPayRequest(String consentId, String tppId, String paymentId, String reason,
                                             String interactionId) {
        DecisionBy decision = new DecisionBy(tppId, reason);
        return decide(consentId, tppId, interactionId,
                request -> request.consume(paymentId, decision, Instant.now(clock)));
    }

    @Override
    @Transactional
    public PayRequestResult rejectPayRequest(String consentId, String tppId, String reason, String interactionId) {
        DecisionBy decision = new DecisionBy(tppId, reason);
        return decide(consentId, tppId, interactionId, request -> request.reject(decision, Instant.now(clock)));
    }

    private PayRequestResult decide(String consentId, String tppId, String interactionId,
                                    UnaryOperator<PayRequest> decision) {
        PayRequest request = repositoryPort.findByConsentIdForUpdate(consentId)
                .orElseThrow(() -> new ResourceNotFoundException("Pay request not found"));
        ensureOwnership(request, tppId);

        PayRequest decided = decision.apply(request);
        if (decided.domainEvents().isEmpty()) {
            // A repeated decision with the same outcome: answer with the current state.
            return new PayRequestResult(decided, false);
        }
        PayRequest saved = repositoryPort.save(decided);
        eventPublisher.publish(decided, decided.domainEvents(), interactionId);

        PayRequestResult result = new PayRequestResult(saved, false);
        // Refresh this replica's cache; other replicas converge within cacheTtl.
        cachePort.putStatus(cacheKey(consentId, tppId), result, Instant.now(clock).plus(settings.cacheTtl()));
        return result;
    }

    private PayRequestResult replay(IdempotencyRecord earlier, String fingerprint) {
        if (!earlier.matches(fingerprint)) {
            throw new IdempotencyKeyConflictException(
                    "Idempotency key was already used with a different request payload");
        }
        PayRequest original = repositoryPort.findByConsentId(earlier.consentId())
                .orElseThrow(() -> new ResourceNotFoundException("Pay request not found"));
        return PayRequestResult.replayOf(original);
    }

    private static void ensureOwnership(PayRequest request, String tppId) {
        if (!request.belongsTo(tppId)) {
            throw new PayRequestAccessDeniedException("Pay request participant mismatch");
        }
    }

    private static String cacheKey(String consentId, String tppId) {
        return "pay-request:" + consentId + ':' + tppId;
    }
}
