package com.enterprise.openfinance.domain.port.output;

import com.enterprise.openfinance.domain.model.consent.ConsentId;
import com.enterprise.shared.domain.CustomerId;

public interface AuditTrailService {
    void recordConsentAuthorization(ConsentId consentId, CustomerId customerId, String authorizationMethod, String ipAddress, String userAgent);
    void recordConsentAuthorizationFailure(ConsentId consentId, CustomerId customerId, String errorMessage);
    void recordConsentRevocation(ConsentId consentId, String revocationReason, String revokedBy, String ipAddress);
    void recordConsentRevocationFailure(ConsentId consentId, String revocationReason, String errorMessage);
}
