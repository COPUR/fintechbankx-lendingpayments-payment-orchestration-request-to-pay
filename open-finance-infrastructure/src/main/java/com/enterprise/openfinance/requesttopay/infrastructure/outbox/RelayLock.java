package com.enterprise.openfinance.requesttopay.infrastructure.outbox;

import java.util.Optional;

/**
 * Cluster-wide "only one relay runs" lock. Held for one relay run, outside
 * any database transaction.
 */
public interface RelayLock {

    /** @return the held lock, or empty when another replica holds it */
    Optional<Held> tryAcquire();

    interface Held extends AutoCloseable {
        @Override
        void close();
    }
}
