package com.enterprise.openfinance.domain.model.consent;

public enum ConsentPurpose {
    DATA_SHARING(90),
    PAYMENT_INITIATION(1);

    private final int recommendedValidityDays;

    ConsentPurpose(int recommendedValidityDays) {
        this.recommendedValidityDays = recommendedValidityDays;
    }

    public int getRecommendedValidityDays() {
        return recommendedValidityDays;
    }
}
