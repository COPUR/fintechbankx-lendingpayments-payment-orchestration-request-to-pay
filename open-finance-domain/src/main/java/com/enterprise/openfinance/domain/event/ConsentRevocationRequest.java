package com.enterprise.openfinance.domain.event;

import com.enterprise.openfinance.domain.model.consent.ConsentId;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ConsentRevocationRequest {
    private ConsentId consentId;
    private String revocationReason;
    private String revokedBy;
    private String ipAddress;
    private String interactionId;
}
