package com.enterprise.openfinance.requesttopay.infrastructure.security;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;

/**
 * DPoP proof replay guard shared by every replica: the SHA-256 of each proof
 * jti is inserted into sc_pay_request_to_pay.dpop_proof_jti (primary key);
 * a second insert of the same jti is a replay.
 */
@Repository
public class JdbcDPoPNonceRepository implements DPoPNonceRepository {

    static final String INSERT = """
            insert into dpop_proof_jti (jti_hash, expires_at) values (:jtiHash, :expiresAt)
            on conflict (jti_hash) do nothing
            """;
    static final String PURGE_EXPIRED = "delete from dpop_proof_jti where expires_at <= :now";

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public JdbcDPoPNonceRepository(NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public boolean saveJtiIfAbsent(String jti, long ttlSeconds) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("jtiHash", sha256(jti))
                .addValue("expiresAt", Timestamp.from(Instant.now(clock).plusSeconds(ttlSeconds)));
        return jdbc.update(INSERT, params) == 1;
    }

    /** @return number of expired jti rows removed */
    public int purgeExpired(Instant now) {
        return jdbc.update(PURGE_EXPIRED, new MapSqlParameterSource("now", Timestamp.from(now)));
    }

    static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
