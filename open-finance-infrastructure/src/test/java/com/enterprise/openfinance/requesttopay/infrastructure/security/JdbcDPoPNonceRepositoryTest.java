package com.enterprise.openfinance.requesttopay.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcDPoPNonceRepositoryTest {

    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final JdbcDPoPNonceRepository repository = new JdbcDPoPNonceRepository(jdbc,
            Clock.fixed(Instant.parse("2026-02-10T10:00:00Z"), ZoneOffset.UTC));

    @Test
    void firstSightOfAJtiIsAcceptedAndStoredHashed() {
        when(jdbc.update(eq(JdbcDPoPNonceRepository.INSERT), any(SqlParameterSource.class))).thenReturn(1);

        assertThat(repository.saveJtiIfAbsent("jti-1", 300)).isTrue();

        ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).update(eq(JdbcDPoPNonceRepository.INSERT), params.capture());
        assertThat(params.getValue().getValue("jtiHash")).isEqualTo(JdbcDPoPNonceRepository.sha256("jti-1"));
        assertThat((String) params.getValue().getValue("jtiHash")).hasSize(64).doesNotContain("jti-1");
    }

    @Test
    void replayedJtiIsRefused() {
        when(jdbc.update(eq(JdbcDPoPNonceRepository.INSERT), any(SqlParameterSource.class))).thenReturn(0);

        assertThat(repository.saveJtiIfAbsent("jti-1", 300)).isFalse();
    }

    @Test
    void purgeRemovesExpiredRows() {
        when(jdbc.update(eq(JdbcDPoPNonceRepository.PURGE_EXPIRED), any(SqlParameterSource.class))).thenReturn(2);

        assertThat(repository.purgeExpired(Instant.parse("2026-02-10T11:00:00Z"))).isEqualTo(2);
    }
}
