package com.enterprise.openfinance.domain.event;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TransformedData {
    private String transformationId;
    private Object transformedPayload;
}
