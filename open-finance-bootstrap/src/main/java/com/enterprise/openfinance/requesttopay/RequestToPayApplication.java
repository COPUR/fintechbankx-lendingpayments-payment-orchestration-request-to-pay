package com.enterprise.openfinance.requesttopay;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * svc-pay-request-to-pay: the request-to-pay capability of the payments
 * context, extracted from enterprise-loan-management-system
 * (open-finance-context, package requesttopay).
 */
@SpringBootApplication
public class RequestToPayApplication {

    public static void main(String[] args) {
        SpringApplication.run(RequestToPayApplication.class, args);
    }
}
