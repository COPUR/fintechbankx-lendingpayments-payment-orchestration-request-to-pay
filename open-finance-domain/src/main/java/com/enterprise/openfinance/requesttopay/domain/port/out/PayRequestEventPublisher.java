package com.enterprise.openfinance.requesttopay.domain.port.out;

import com.enterprise.openfinance.requesttopay.domain.event.PayRequestDomainEvent;
import com.enterprise.openfinance.requesttopay.domain.model.PayRequest;

import java.util.List;

/**
 * Publishes the events a pay request registered. Implementations must write
 * them in the caller's transaction (transactional outbox) so that state and
 * events commit or roll back together.
 *
 * @param correlationId the FAPI interaction id of the request that caused the change
 */
public interface PayRequestEventPublisher {

    void publish(PayRequest payRequest, List<PayRequestDomainEvent> events, String correlationId);
}
