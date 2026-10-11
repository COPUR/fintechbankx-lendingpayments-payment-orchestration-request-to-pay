package com.enterprise.openfinance.requesttopay.infrastructure.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Session-level Postgres advisory lock on a connection of its own, kept in
 * autocommit mode (no transaction is open while the relay sends to Kafka).
 * If the pod dies, the session ends and Postgres releases the lock. On
 * release the lock is unlocked explicitly; if that fails the connection is
 * aborted instead of going back to the pool still holding the lock.
 */
public class PostgresSessionRelayLock implements RelayLock {

    private static final Logger log = LoggerFactory.getLogger(PostgresSessionRelayLock.class);

    private final DataSource dataSource;
    private final long key;

    public PostgresSessionRelayLock(DataSource dataSource, long key) {
        this.dataSource = dataSource;
        this.key = key;
    }

    @Override
    public Optional<Held> tryAcquire() {
        Connection connection = null;
        try {
            connection = dataSource.getConnection();
            connection.setAutoCommit(true);
            if (!call(connection, "select pg_try_advisory_lock(?)")) {
                connection.close();
                return Optional.empty();
            }
            Connection held = connection;
            return Optional.of(() -> release(held));
        } catch (SQLException e) {
            closeQuietly(connection);
            throw new IllegalStateException("Outbox relay lock could not be taken", e);
        }
    }

    private void release(Connection connection) {
        try {
            if (!call(connection, "select pg_advisory_unlock(?)")) {
                abort(connection);
            }
        } catch (SQLException e) {
            log.warn("Outbox relay lock release failed; dropping the connection", e);
            abort(connection);
        } finally {
            closeQuietly(connection);
        }
    }

    private boolean call(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, key);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getBoolean(1);
            }
        }
    }

    private static void abort(Connection connection) {
        try {
            connection.abort(Runnable::run);
        } catch (SQLException e) {
            log.warn("Could not abort the outbox relay lock connection", e);
        }
    }

    private static void closeQuietly(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException e) {
            log.debug("Closing the outbox relay lock connection failed", e);
        }
    }
}
