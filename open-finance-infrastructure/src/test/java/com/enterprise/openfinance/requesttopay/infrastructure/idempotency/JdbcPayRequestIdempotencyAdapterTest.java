package com.enterprise.openfinance.requesttopay.infrastructure.idempotency;

import com.enterprise.openfinance.requesttopay.domain.model.IdempotencyRecord;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** SQL behaviour is covered against PostgreSQL in RequestToPayServiceIT. */
class JdbcPayRequestIdempotencyAdapterTest {

    private static final Instant NOW = Instant.parse("2026-02-10T10:00:00Z");

    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final JdbcPayRequestIdempotencyAdapter adapter = new JdbcPayRequestIdempotencyAdapter(jdbc);

    @Test
    void firstUseReservesTheKey() {
        when(jdbc.update(eq(JdbcPayRequestIdempotencyAdapter.RESERVE), any(SqlParameterSource.class))).thenReturn(1);

        assertThat(adapter.reserve("TPP", "k", "f", "CONS-1", NOW, NOW.plusSeconds(60))).isEmpty();
        verify(jdbc).update(eq(JdbcPayRequestIdempotencyAdapter.DELETE_EXPIRED_KEY), any(SqlParameterSource.class));
        verify(jdbc, never()).query(eq(JdbcPayRequestIdempotencyAdapter.FIND), any(SqlParameterSource.class),
                any(RowMapper.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void reusedKeyReturnsTheEarlierUse() {
        when(jdbc.update(eq(JdbcPayRequestIdempotencyAdapter.RESERVE), any(SqlParameterSource.class))).thenReturn(0);
        when(jdbc.query(eq(JdbcPayRequestIdempotencyAdapter.FIND), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of(new IdempotencyRecord("CONS-0", "f0")));

        assertThat(adapter.reserve("TPP", "k", "f", "CONS-1", NOW, NOW.plusSeconds(60)))
                .contains(new IdempotencyRecord("CONS-0", "f0"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void keyNeitherReservedNorFoundIsAnError() {
        when(jdbc.update(eq(JdbcPayRequestIdempotencyAdapter.RESERVE), any(SqlParameterSource.class))).thenReturn(0);
        when(jdbc.query(eq(JdbcPayRequestIdempotencyAdapter.FIND), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());

        assertThatThrownBy(() -> adapter.reserve("TPP", "k", "f", "CONS-1", NOW, NOW.plusSeconds(60)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void purgeRemovesExpiredKeys() {
        when(jdbc.update(eq(JdbcPayRequestIdempotencyAdapter.PURGE_EXPIRED), any(SqlParameterSource.class)))
                .thenReturn(3);

        assertThat(adapter.purgeExpired(NOW)).isEqualTo(3);
    }
}
