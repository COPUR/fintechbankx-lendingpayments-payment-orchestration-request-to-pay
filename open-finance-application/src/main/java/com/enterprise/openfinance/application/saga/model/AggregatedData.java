package com.enterprise.openfinance.application.saga.model;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
public class AggregatedData {
    private AggregationId aggregationId;
    private List<PlatformData> platformDataList;
    private int sourceCount;
    private long dataSize;
    private List<String> dataSources;
    private Instant aggregatedAt;
}
