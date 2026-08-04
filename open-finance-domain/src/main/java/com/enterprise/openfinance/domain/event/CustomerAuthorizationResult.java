package com.enterprise.openfinance.domain.event;

import com.enterprise.openfinance.domain.model.consent.ConsentId;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class CustomerAuthorizationResult {
    private ConsentId consentId;
    private boolean authorized;
    private boolean timeout;
    private Instant authorizedAt;

    public static CustomerAuthorizationResult authorized(ConsentId consentId, Instant authorizedAt) {
        return CustomerAuthorizationResult.builder()
                .consentId(consentId)
                .authorized(true)
                .timeout(false)
                .authorizedAt(authorizedAt)
                .build();
    }

    public static CustomerAuthorizationResult timeout(ConsentId consentId) {
        return CustomerAuthorizationResult.builder()
                .consentId(consentId)
                .authorized(false)
                .timeout(true)
                .build();
    }
}
