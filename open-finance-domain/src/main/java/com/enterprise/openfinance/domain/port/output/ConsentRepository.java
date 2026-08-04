package com.enterprise.openfinance.domain.port.output;

import com.enterprise.openfinance.domain.model.consent.Consent;
import com.enterprise.openfinance.domain.model.participant.ParticipantId;
import com.enterprise.shared.domain.CustomerId;

import java.util.List;
import java.util.Optional;

public interface ConsentRepository {
    List<Consent> findActiveConsentsByCustomer(CustomerId customerId, Optional<ParticipantId> participantId);
}
