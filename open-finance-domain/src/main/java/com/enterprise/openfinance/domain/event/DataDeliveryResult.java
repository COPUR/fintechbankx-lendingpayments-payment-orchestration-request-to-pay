package com.enterprise.openfinance.domain.event;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class DataDeliveryResult {
    private boolean success;
    private String errorMessage;
    private String errorCode;
    private Instant deliveryTimestamp;
}
