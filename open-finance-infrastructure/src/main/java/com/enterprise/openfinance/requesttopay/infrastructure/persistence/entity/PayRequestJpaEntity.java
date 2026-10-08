package com.enterprise.openfinance.requesttopay.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import org.springframework.data.domain.Persistable;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Row of sc_pay_request_to_pay.pay_request. Separate from the domain record;
 * mapped by PayRequestMapper. {@code version} is the optimistic-lock column and
 * equals the domain version once stored.
 */
@Entity
@Table(name = "pay_request")
public class PayRequestJpaEntity implements Persistable<String> {

    @Id
    @Column(name = "consent_id", nullable = false, updatable = false, length = 64)
    private String consentId;

    @Column(name = "tpp_id", nullable = false, updatable = false, length = 128)
    private String tppId;

    @Column(name = "debtor_id", nullable = false, updatable = false, length = 128)
    private String psuId;

    @Column(name = "creditor_name", nullable = false, updatable = false, length = 140)
    private String creditorName;

    @Column(name = "amount", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, updatable = false, length = 3)
    private String currency;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "payment_id", length = 64)
    private String paymentId;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Transient
    private boolean isNew;

    protected PayRequestJpaEntity() {
        // JPA
    }

    public PayRequestJpaEntity(String consentId, String tppId, String psuId, String creditorName,
                               BigDecimal amount, String currency, String status, Instant requestedAt,
                               Instant updatedAt, String paymentId) {
        this(consentId, tppId, psuId, creditorName, amount, currency, status, requestedAt, updatedAt, paymentId,
                null, true);
    }

    /**
     * @param version stored version this row is expected to have before the write
     *                ({@code null} for a new row)
     */
    public PayRequestJpaEntity(String consentId, String tppId, String psuId, String creditorName,
                               BigDecimal amount, String currency, String status, Instant requestedAt,
                               Instant updatedAt, String paymentId, Long version, boolean isNew) {
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
        this.version = version;
        this.isNew = isNew;
    }

    @Override
    public String getId() {
        return consentId;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markStored() {
        this.isNew = false;
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

    public Long getVersion() {
        return version;
    }
}
