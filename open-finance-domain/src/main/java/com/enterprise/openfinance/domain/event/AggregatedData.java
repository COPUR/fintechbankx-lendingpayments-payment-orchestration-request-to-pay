package com.enterprise.openfinance.domain.event;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
public class AggregatedData {
    private String aggregationId;
    private List<PlatformData> platformDataList;
    private int sourceCount;
    private long dataSize;
    private List<String> dataSources;
    private Instant aggregatedAt;
}
