package com.enterprise.openfinance.requesttopay.domain.port.out;

import com.enterprise.openfinance.requesttopay.domain.model.PayRequest;
import java.util.Optional;

public interface PayRequestRepositoryPort {
    PayRequest save(PayRequest payRequest);
    Optional<PayRequest> findByConsentId(String consentId);
}
