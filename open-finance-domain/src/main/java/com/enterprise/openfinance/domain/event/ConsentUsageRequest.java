package com.enterprise.openfinance.domain.event;

import com.enterprise.openfinance.domain.model.consent.ConsentId;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ConsentUsageRequest {
    private ConsentId consentId;
    private String dataType;
    private long dataSize;
}
