package com.enterprise.openfinance.requesttopay.infrastructure.security;

public interface DPoPNonceRepository {
    /**
     * Saves the DPoP JTI if it's not already present, preventing replay attacks.
     *
     * @param jti The JWT ID from the DPoP proof.
     * @param ttlSeconds The time-to-live for the JTI in seconds.
     * @return true if the JTI was successfully saved (i.e., it was not a replay), false otherwise.
     */
    boolean saveJtiIfAbsent(String jti, long ttlSeconds);
}
