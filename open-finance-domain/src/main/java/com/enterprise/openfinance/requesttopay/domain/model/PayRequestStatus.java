package com.enterprise.openfinance.requesttopay.domain.model;

public enum PayRequestStatus {
    AWAITING_AUTHORISATION("AwaitingAuthorisation", false),
    REJECTED("Rejected", true),
    CONSUMED("Consumed", true);

    private final String apiValue;
    private final boolean terminal;

    PayRequestStatus(String apiValue, boolean terminal) {
        this.apiValue = apiValue;
        this.terminal = terminal;
    }

    public String apiValue() {
        return apiValue;
    }

    public boolean isFinal() {
        return terminal;
    }
}
