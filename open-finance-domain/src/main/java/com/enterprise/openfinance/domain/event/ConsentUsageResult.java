package com.enterprise.openfinance.domain.event;

import com.enterprise.openfinance.domain.model.consent.ConsentId;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class ConsentUsageResult {
    private ConsentId consentId;
    private Instant usageTimestamp;
    private String dataType;
    private long remainingUsageQuota;
}
