package com.enterprise.openfinance.domain.event;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EncryptedData {
    private String encryptionId;
    private byte[] encryptedPayload;
}
