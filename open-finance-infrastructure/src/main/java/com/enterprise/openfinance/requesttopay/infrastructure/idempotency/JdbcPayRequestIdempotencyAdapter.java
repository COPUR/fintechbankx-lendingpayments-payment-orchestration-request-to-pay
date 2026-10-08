package com.enterprise.openfinance.requesttopay.infrastructure.idempotency;

import com.enterprise.openfinance.requesttopay.domain.model.IdempotencyRecord;
import com.enterprise.openfinance.requesttopay.domain.port.out.PayRequestIdempotencyPort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * x-idempotency-key store in sc_pay_request_to_pay.pay_request_idempotency,
 * unique on (tpp_id, idempotency_key). The insert runs in the pay request's
 * transaction: a concurrent duplicate waits on the unique index and then sees
 * the committed first use; a failed create rolls the reservation back.
 * An expired key (expires_at reached) is deleted first, so the key can be
 * reused for a new request; the earlier pay request stays. Only this table is
 * unique on the key: pay_request has no (tpp_id, key) constraint that a reuse
 * could violate.
 */
@Component
public class JdbcPayRequestIdempotencyAdapter implements PayRequestIdempotencyPort {

    static final String DELETE_EXPIRED_KEY = """
            delete from pay_request_idempotency
            where tpp_id = :tppId and idempotency_key = :key and expires_at <= :now
            """;
    static final String RESERVE = """
            insert into pay_request_idempotency
                (tpp_id, idempotency_key, request_fingerprint, consent_id, created_at, expires_at)
            values (:tppId, :key, :fingerprint, :consentId, :now, :expiresAt)
            on conflict (tpp_id, idempotency_key) do nothing
            """;
    static final String FIND = """
            select consent_id, request_fingerprint from pay_request_idempotency
            where tpp_id = :tppId and idempotency_key = :key
            """;
    static final String PURGE_EXPIRED = "delete from pay_request_idempotency where expires_at <= :now";

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcPayRequestIdempotencyAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<IdempotencyRecord> reserve(String tppId, String idempotencyKey, String requestFingerprint,
                                               String consentId, Instant now, Instant expiresAt) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tppId", tppId)
                .addValue("key", idempotencyKey)
                .addValue("fingerprint", requestFingerprint)
                .addValue("consentId", consentId)
                .addValue("now", Timestamp.from(now))
                .addValue("expiresAt", Timestamp.from(expiresAt));

        jdbc.update(DELETE_EXPIRED_KEY, params);
        if (jdbc.update(RESERVE, params) == 1) {
            return Optional.empty();
        }
        List<IdempotencyRecord> earlier = jdbc.query(FIND, params,
                (rs, row) -> new IdempotencyRecord(rs.getString("consent_id"), rs.getString("request_fingerprint")));
        if (earlier.isEmpty()) {
            throw new IllegalStateException("Idempotency key neither reserved nor found");
        }
        return Optional.of(earlier.getFirst());
    }

    /** @return number of expired keys removed */
    @Transactional
    public int purgeExpired(Instant now) {
        return jdbc.update(PURGE_EXPIRED, new MapSqlParameterSource("now", Timestamp.from(now)));
    }
}
