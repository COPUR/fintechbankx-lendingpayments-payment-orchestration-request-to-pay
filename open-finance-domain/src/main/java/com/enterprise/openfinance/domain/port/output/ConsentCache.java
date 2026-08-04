package com.enterprise.openfinance.domain.port.output;

import com.enterprise.openfinance.domain.model.consent.Consent;
import com.enterprise.openfinance.domain.model.consent.ConsentId;

import java.util.Optional;

public interface ConsentCache {
    void put(ConsentId consentId, Consent consent);
    Optional<Consent> get(ConsentId consentId);
}
