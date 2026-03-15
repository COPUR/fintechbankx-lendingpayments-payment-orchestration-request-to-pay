package com.enterprise.openfinance.requesttopay.domain.port.out;

import com.enterprise.openfinance.requesttopay.domain.model.PayRequest;

public interface PayRequestNotificationPort {
    void notifyPayRequestCreated(PayRequest request);

    void notifyPayRequestFinalized(PayRequest request);
}
