package com.enterprise.openfinance.application.saga.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PlatformData {
    private String sourcePlatform;
    private long dataSize;
    private Object payload;
}
