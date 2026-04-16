package com.enterprise.openfinance.requesttopay.infrastructure.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "pay_requests", schema = "pis")
public class PayRequestJpaEntity {

    @Id
    @Column(name = "request_id", nullable = false)
    private String consentId;

    @Column(name = "tpp_id", nullable = false)
    private String tppId;

    @Column(name = "debtor_id", nullable = false)
    private String psuId;

    @Column(name = "creditor_name", nullable = false)
    private String creditorName;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "payment_id")
    private String paymentId;

    protected PayRequestJpaEntity() {} // JPA

    public PayRequestJpaEntity(String consentId, String tppId, String psuId, String creditorName,
                               BigDecimal amount, String currency, String status, Instant requestedAt,
                               Instant updatedAt, String paymentId) {
        this.consentId = consentId;
        this.tppId = tppId;
        this.psuId = psuId;
        this.creditorName = creditorName;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.requestedAt = requestedAt;
        this.updatedAt = updatedAt;
        this.paymentId = paymentId;
    }

    public String getConsentId() {
        return consentId;
    }

    public String getTppId() {
        return tppId;
    }

    public String getPsuId() {
        return psuId;
    }

    public String getCreditorName() {
        return creditorName;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getStatus() {
        return status;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getPaymentId() {
        return paymentId;
    }
}
