package com.enterprise.openfinance.domain.event;

import com.enterprise.openfinance.domain.model.consent.ConsentId;
import com.enterprise.openfinance.domain.model.consent.ConsentStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ConsentRevocationResult {
    private ConsentId consentId;
    private ConsentStatus status;
    private LocalDateTime revokedAt;
    private String revocationReason;
    private String interactionId;
}
