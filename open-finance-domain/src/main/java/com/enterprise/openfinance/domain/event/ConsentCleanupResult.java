package com.enterprise.openfinance.domain.event;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class ConsentCleanupResult {
    private int processedCount;
    private int failedCount;
    private Instant cleanupTimestamp;
}
