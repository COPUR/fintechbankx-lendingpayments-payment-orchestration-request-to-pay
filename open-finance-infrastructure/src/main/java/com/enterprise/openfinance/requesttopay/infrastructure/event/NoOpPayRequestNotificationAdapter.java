package com.enterprise.openfinance.requesttopay.infrastructure.event;

import com.enterprise.openfinance.requesttopay.domain.model.PayRequest;
import com.enterprise.openfinance.requesttopay.domain.port.out.PayRequestNotificationPort;
import org.springframework.stereotype.Component;

@Component
public class NoOpPayRequestNotificationAdapter implements PayRequestNotificationPort {

    @Override
    public void notifyPayRequestCreated(PayRequest request) {
        // no-op for sandbox
    }

    @Override
    public void notifyPayRequestFinalized(PayRequest request) {
        // no-op for sandbox
    }
}
