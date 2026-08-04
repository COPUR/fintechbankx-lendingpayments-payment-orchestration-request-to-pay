package com.enterprise.openfinance.domain.port.output;

import java.util.Map;

public interface SecurityComplianceService {
    void validateInputSecurity(Map<String, Object> inputData);
}
