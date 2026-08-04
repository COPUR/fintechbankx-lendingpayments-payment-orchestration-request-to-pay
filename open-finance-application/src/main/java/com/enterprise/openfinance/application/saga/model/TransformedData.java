package com.enterprise.openfinance.application.saga.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TransformedData {
    private String transformationId;
    private Object transformedPayload;
}
