package com.enterprise.openfinance.requesttopay.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PayRequestSettingsTest {

    @Test
    void shouldCreateSettingsWithPositiveValues() {
        PayRequestSettings settings = new PayRequestSettings(Duration.ofMinutes(10));

        assertThat(settings.cacheTtl()).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void shouldRejectNonPositiveTtl() {
        assertThatThrownBy(() -> new PayRequestSettings(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cacheTtl");

        assertThatThrownBy(() -> new PayRequestSettings(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cacheTtl");
    }
}
