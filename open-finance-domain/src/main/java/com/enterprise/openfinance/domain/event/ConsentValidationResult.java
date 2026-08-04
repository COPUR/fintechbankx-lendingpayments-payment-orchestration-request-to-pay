package com.enterprise.openfinance.domain.event;

import lombok.Builder;
import lombok.Data;

import java.util.Set;

@Data
@Builder
public class ConsentValidationResult {
    private boolean valid;
    private Set<String> violations;
}
