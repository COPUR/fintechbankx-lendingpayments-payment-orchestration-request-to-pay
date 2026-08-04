package com.enterprise.openfinance.domain.event;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RateLimitResult {
    private boolean allowed;
    private String errorMessage;
    private String errorCode;
}
