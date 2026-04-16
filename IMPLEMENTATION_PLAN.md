**Phase 1: Pure Domain Modeling (Hexagonal Architecture Guardrails)**
The Request to Pay (RtP) domain must be framework-independent, with no JPA annotations leaking into the core business logic. 

*   **To-Do:**
    1. Define the `PayRequest` Aggregate Root to track the state machine: `AwaitingAuthorisation` ➔ `Rejected` | `Consumed`.
    2. Implement value objects for `CreditorId`, `DebtorId`, and `Money`.
    3. Define the domain events: `PayRequestCreatedEvent`, `PayRequestRejectedEvent`, and `PayRequestAcceptedEvent`.
    4. Define the `PayRequestRepositoryPort` (Out Port) and `RequestToPayUseCase` (In Port).
*   **Java/Spring Dependencies:** None in the domain layer. Use core Java 23 features (Records, Pattern Matching).
*   **Sample Domain State Machine Logic:**
    *   If Debtor accepts an already `Consumed` request, throw a domain exception to trigger a `400 Bad Request`.

**Phase 2: Persistence & Idempotency Adapters (Infrastructure Layer)**
Implement the infrastructure adapters to persist the RtP state using PostgreSQL (System of Record) and Redis (Idempotency Cache).

*   **To-Do:**
    1. Create `PayRequestJpaEntity` and a mapper to translate it to/from the pure `PayRequest` domain object.
    2. Write Flyway migration scripts for `pis.pay_requests` and `pis.pay_request_audit` tables.
    3. Implement a Redis-based idempotency shield to prevent duplicate requests from the Creditor TPP.
*   **Java/Spring Dependencies:** 
    *   `org.springframework.boot:spring-boot-starter-data-jpa`
    *   `org.postgresql:postgresql:42.7.1`
    *   `org.springframework.boot:spring-boot-starter-data-redis`
*   **Sample Data (PostgreSQL Parameterized Query):**
    ```sql
    BEGIN;
    INSERT INTO pis.pay_requests (request_id, creditor_id, debtor_id, amount, currency, status, created_at) 
    VALUES ('REQ-778899', 'CRED-UTIL-123', 'USR-9876', 500.00, 'AED', 'AwaitingAuthorisation', CURRENT_TIMESTAMP);
    COMMIT;
    ```

**Phase 3: Notification Gateway & Event Streaming**
When an RtP is created, the debtor must receive an SLA-bound notification (Push/SMS). 

*   **To-Do:**
    1. Configure a Kafka Publisher adapter to emit `PayRequestCreatedEvent`.
    2. Build the `NotificationService` listener to trigger the Push Notification/SMS to the debtor's mobile app.
*   **Java/Spring Dependencies:** 
    *   `org.springframework.kafka:spring-kafka`
    *   `com.fasterxml.jackson.core:jackson-databind:2.16.0`
*   **Sample Data (Kafka Event Payload):**
    ```json
    {
      "eventId": "evt-rtp-001",
      "eventType": "PayRequestCreatedEvent",
      "aggregateId": "REQ-778899",
      "occurredOn": "2026-04-16T10:35:01Z",
      "data": {
        "creditorName": "Utilities Co",
        "amount": 500.00,
        "currency": "AED",
        "debtorId": "USR-9876"
      }
    }
    ```

**Phase 4: API Web Adapters & Zero-Trust Security (FAPI 2.0)**
Expose the REST endpoints for the Creditor TPP, securing them behind the API Gateway and Istio Service Mesh using strict DPoP and FAPI 2.0 profiles.

*   **To-Do:**
    1. Implement `RequestToPayController` mapped to `POST /api/v1/pay-requests` and `GET /api/v1/pay-requests/{ConsentId}`.
    2. Protect the endpoints with `@DPoPSecured` and `@FAPISecured` annotations.
    3. Ensure `X-Idempotency-Key` and `X-FAPI-Interaction-ID` headers are strictly parsed and validated.
*   **Java/Spring Dependencies:**
    *   `org.springframework.boot:spring-boot-starter-web`
    *   `org.springframework.boot:spring-boot-starter-oauth2-resource-server`
    *   `com.nimbusds:nimbus-jose-jwt:9.47` (For DPoP proof validation)
*   **Sample Request (TC-RTP-001 Creation):**
    ```http
    POST /api/v1/pay-requests HTTP/1.1
    Host: api.banking.example.com
    Authorization: DPoP eyJhbGci...
    DPoP: eyJ0eXAi...
    X-FAPI-Interaction-ID: 550e8400-e29b-41d4-a716-446655440000
    X-Idempotency-Key: req-rtp-5544-3322
    Content-Type: application/json

    {
      "creditorName": "Utilities Co",
      "debtorIdentifier": "+971501234567",
      "amount": 500.00,
      "currency": "AED",
      "reference": "Monthly Invoice"
    }
    ```
*   **Sample Response (TC-RTP-002 Status):**
    ```http
    HTTP/1.1 201 Created
    X-FAPI-Interaction-ID: 550e8400-e29b-41d4-a716-446655440000

    {
      "consentId": "REQ-778899",
      "status": "AwaitingAuthorisation",
      "createdAt": "2026-04-16T10:35:00Z"
    }
    ```

**Phase 5: Debtor Action Orchestration (Accept/Reject)**
Integrate the Debtor's response from the CAAP (Authentication Provider) to finalize the RtP and initiate the actual payment orchestration saga if accepted.

*   **To-Do:**
    1. Implement `POST /api/v1/pay-requests/{ConsentId}/reject`: Updates status to `Rejected`.
    2. Implement `POST /api/v1/pay-requests/{ConsentId}/accept`: Consumes the request and dispatches a command to the `Payment Initiation Service (PIS)` to execute the `ProcessLoanPaymentCommand`.
    3. Enforce the idempotency invariant: If the debtor attempts to accept an already `Consumed` or `Rejected` request, throw a `400 Bad Request` indicating "Request already finalized".

**Phase 6: CI/CD Quality Gates & Testing Automation**
Before deployment, enforce the strict banking architecture quality gates using the verified CI/CD setup.

*   **To-Do:**
    1. **ArchUnit Check:** Run `./gradlew test --no-daemon` to execute `HexagonalDomainLayerPurity` checks, ensuring the RtP domain has zero infrastructure leakage.
    2. **JaCoCo Coverage:** Run `./tools/validation/validate-coverage-gates.sh --threshold 85` to enforce the mandatory >= 85% line coverage.
    3. **Postman Automation:** Add the RtP Test Suite to Newman, covering `TC-RTP-001` through `TC-RTP-006`.
    4. **Performance Validation:** Utilize K6 (`stress_test_payments.js` equivalent) to ensure the RtP endpoint responds in `< 500ms` (P95) and properly rejects spam duplicate payloads via the Redis idempotency lock.