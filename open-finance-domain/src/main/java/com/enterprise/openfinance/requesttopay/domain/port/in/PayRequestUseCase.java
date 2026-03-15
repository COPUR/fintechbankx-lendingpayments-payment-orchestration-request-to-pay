package com.enterprise.openfinance.requesttopay.domain.port.in;

import com.enterprise.openfinance.requesttopay.domain.command.CreatePayRequestCommand;
import com.enterprise.openfinance.requesttopay.domain.model.PayRequestResult;
import com.enterprise.openfinance.requesttopay.domain.query.GetPayRequestStatusQuery;

public interface PayRequestUseCase {

    PayRequestResult createPayRequest(CreatePayRequestCommand command);

    PayRequestResult getPayRequestStatus(GetPayRequestStatusQuery query);

    PayRequestResult acceptPayRequest(String consentId, String tppId, String paymentId, String interactionId);

    PayRequestResult rejectPayRequest(String consentId, String tppId, String interactionId);
}
