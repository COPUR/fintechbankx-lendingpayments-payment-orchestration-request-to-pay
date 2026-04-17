CREATE SCHEMA IF NOT EXISTS pis;

CREATE TABLE pis.pay_requests (
    request_id VARCHAR(255) PRIMARY KEY,
    tpp_id VARCHAR(255) NOT NULL,
    debtor_id VARCHAR(255) NOT NULL,
    creditor_name VARCHAR(255) NOT NULL,
    amount DECIMAL(19, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    payment_id VARCHAR(255)
);

CREATE INDEX idx_pay_requests_status ON pis.pay_requests(status);
CREATE INDEX idx_pay_requests_debtor_id ON pis.pay_requests(debtor_id);
