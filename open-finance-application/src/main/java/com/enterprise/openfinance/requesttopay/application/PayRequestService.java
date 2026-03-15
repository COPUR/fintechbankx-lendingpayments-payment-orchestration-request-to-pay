package com.enterprise.openfinance.requesttopay.application;

import com.enterprise.openfinance.requesttopay.domain.command.CreatePayRequestCommand;
import com.enterprise.openfinance.requesttopay.domain.exception.PayRequestFinalizedException;
import com.enterprise.openfinance.requesttopay.domain.exception.ResourceNotFoundException;
import com.enterprise.openfinance.requesttopay.domain.model.PayRequest;
import com.enterprise.openfinance.requesttopay.domain.model.PayRequestResult;
import com.enterprise.openfinance.requesttopay.domain.model.PayRequestSettings;
import com.enterprise.openfinance.requesttopay.domain.model.PayRequestStatus;
import com.enterprise.openfinance.requesttopay.domain.port.in.PayRequestUseCase;
import com.enterprise.openfinance.requesttopay.domain.port.out.PayRequestCachePort;
import com.enterprise.openfinance.requesttopay.domain.port.out.PayRequestNotificationPort;
import com.enterprise.openfinance.requesttopay.domain.port.out.PayRequestRepositoryPort;
import com.enterprise.openfinance.requesttopay.domain.query.GetPayRequestStatusQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Supplier;

@Service
public class PayRequestService implements PayRequestUseCase {

    private final PayRequestRepositoryPort repositoryPort;
    private final PayRequestCachePort cachePort;
    private final PayRequestNotificationPort notificationPort;
    private final PayRequestSettings settings;
    private final Clock clock;
    private final Supplier<String> consentIdGenerator;

    public PayRequestService(PayRequestRepositoryPort repositoryPort,
                             PayRequestCachePort cachePort,
                             PayRequestNotificationPort notificationPort,
                             PayRequestSettings settings,
                             Clock clock,
                             Supplier<String> consentIdGenerator) {
        this.repositoryPort = repositoryPort;
        this.cachePort = cachePort;
        this.notificationPort = notificationPort;
        this.settings = settings;
        this.clock = clock;
        this.consentIdGenerator = consentIdGenerator;
    }

    @Override
    @Transactional
    public PayRequestResult createPayRequest(CreatePayRequestCommand command) {
        Instant now = Instant.now(clock);
        String consentId = consentIdGenerator.get();

        PayRequest request = new PayRequest(
                consentId,
                command.tppId(),
                command.psuId(),
                command.creditorName(),
                command.amount(),
                command.currency(),
                PayRequestStatus.AWAITING_AUTHORISATION,
                command.requestedAt(),
                now,
                null
        );

        PayRequest saved = repositoryPort.save(request);
        notificationPort.notifyPayRequestCreated(saved);
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

        PayRequest request = repositoryPort.findById(query.consentId())
                .orElseThrow(() -> new ResourceNotFoundException("Pay request not found"));

        ensureOwnership(request, query.tppId());

        PayRequestResult result = new PayRequestResult(request, false);
        cachePort.putStatus(cacheKey, result, now.plus(settings.cacheTtl()));
        return result;
    }

    @Override
    @Transactional
    public PayRequestResult acceptPayRequest(String consentId, String tppId, String paymentId, String interactionId) {
        PayRequest request = repositoryPort.findById(consentId)
                .orElseThrow(() -> new ResourceNotFoundException("Pay request not found"));
        ensureOwnership(request, tppId);
        if (request.isFinalized()) {
            throw new PayRequestFinalizedException("Pay request already finalized");
        }
        PayRequest consumed = request.consume(paymentId, Instant.now(clock));
        PayRequest saved = repositoryPort.save(consumed);
        notificationPort.notifyPayRequestFinalized(saved);
        return new PayRequestResult(saved, false);
    }

    @Override
    @Transactional
    public PayRequestResult rejectPayRequest(String consentId, String tppId, String interactionId) {
        PayRequest request = repositoryPort.findById(consentId)
                .orElseThrow(() -> new ResourceNotFoundException("Pay request not found"));
        ensureOwnership(request, tppId);
        if (request.isFinalized()) {
            throw new PayRequestFinalizedException("Pay request already finalized");
        }
        PayRequest rejected = request.reject(Instant.now(clock));
        PayRequest saved = repositoryPort.save(rejected);
        notificationPort.notifyPayRequestFinalized(saved);
        return new PayRequestResult(saved, false);
    }

    private static void ensureOwnership(PayRequest request, String tppId) {
        if (!request.belongsTo(tppId)) {
            throw new IllegalArgumentException("Pay request participant mismatch");
        }
    }

    private static String cacheKey(String consentId, String tppId) {
        return "pay-request:" + consentId + ':' + tppId;
    }
}
