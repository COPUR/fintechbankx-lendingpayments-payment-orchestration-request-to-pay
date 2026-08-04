package com.enterprise.openfinance.domain.event;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PlatformData {
    private String sourcePlatform;
    private long dataSize;
    private Object payload;
}
