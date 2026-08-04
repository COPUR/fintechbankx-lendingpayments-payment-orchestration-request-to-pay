package com.enterprise.openfinance.application.saga.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EncryptedData {
    private String encryptionId;
    private byte[] encryptedPayload;
}
