package com.enterprise.openfinance.requesttopay.domain.port.in;

import com.enterprise.openfinance.requesttopay.domain.command.CreatePayRequestCommand;
import com.enterprise.openfinance.requesttopay.domain.model.PayRequestResult;
import com.enterprise.openfinance.requesttopay.domain.query.GetPayRequestStatusQuery;

public interface PayRequestUseCase {

    PayRequestResult createPayRequest(CreatePayRequestCommand command);

    PayRequestResult getPayRequestStatus(GetPayRequestStatusQuery query);

    /**
     * The requesting TPP reports acceptance with {@code paymentId}. Repeating it with the same
     * paymentId returns the current state and publishes nothing.
     *
     * @param reason optional free text, carried in the event
     */
    PayRequestResult acceptPayRequest(String consentId, String tppId, String paymentId, String reason,
                                      String interactionId);

    /**
     * The requesting TPP reports that the debtor declined. Repeating it returns the current
     * state and publishes nothing.
     *
     * @param reason optional free text, carried in the event
     */
    PayRequestResult rejectPayRequest(String consentId, String tppId, String reason, String interactionId);
}
