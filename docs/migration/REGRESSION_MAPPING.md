# Regression mapping: monolith request-to-pay -> svc-pay-request-to-pay

Status: **Proposed**. Source: `enterprise-loan-management-system` (branch master),
`open-finance-context/open-finance-infrastructure/.../requesttopay/infrastructure/rest/PayRequestController.java`
(`@RequestMapping("/open-finance/v1")`). Target: this repository,
`open-finance-infrastructure/.../requesttopay/infrastructure/rest/PayRequestController.java`
(`@RequestMapping("/api/v1/pay-requests")`). The path change was already on `main` of this repository (extraction
seed); this branch keeps it.

## Endpoints

| Monolith | New service | Request / response | Status codes |
|---|---|---|---|
| `POST /open-finance/v1/par` | `POST /api/v1/pay-requests` | Same body (`Data.PsuId`, `Data.CreditorName`, `Data.InstructedAmount.{Amount,Currency}`) and response (`Data.ConsentId`, `Data.Status`, `Links.Self`). `Location`/`Links.Self` now `/api/v1/pay-requests/{id}` (was `/open-finance/v1/payment-consents/{id}`). New response header `X-Idempotent-Replay`. | 201 unchanged; new 409 `IDEMPOTENCY_KEY_REUSED` (same key, other payload); 400 on missing `X-Idempotency-Key`, invalid amount format or currency |
| `GET /open-finance/v1/payment-consents/{consentId}` | `GET /api/v1/pay-requests/{consentId}` | Same body (`Data.ConsentId`, `Data.Status`, `Data.PaymentId`), `ETag` / `If-None-Match` -> 304 unchanged | 200/304/404 unchanged; other TPP 400 -> **403** |
| `POST /open-finance/v1/payment-consents/{consentId}/accept` | `POST /api/v1/pay-requests/{consentId}/accept` | Same body `{"paymentId"}` and response | 201 unchanged; finalized 400 `REQUEST_FINALIZED` unchanged; other TPP 400 -> **403**; concurrent change 409 `CONCURRENT_UPDATE` |
| `POST /open-finance/v1/payment-consents/{consentId}/reject` | `POST /api/v1/pay-requests/{consentId}/reject` | Same body and response | 200 unchanged; finalized 400; other TPP 400 -> **403** |
| `api/openapi/request-to-pay-service.yaml` paths `/par`, `/par/{requestId}[/accept|/reject]` | not served by either implementation | spec field names (`DebtorIdentifier`, `RequestId`) differ from both controllers | open question for the contract owner (ADR needed to re-version) |

## Intentional behaviour changes

| # | Change | Why | Test |
|---|---|---|---|
| 1 | Token required and validated (issuer, signature, `aud` contains `svc-pay-request-to-pay`); monolith only checked that `Authorization` and `DPoP` headers were non-blank | platform contract (Keycloak audience) | `AudienceValidatorTest`, `PayRequestControllerDPoPIntegrationTest` |
| 2 | TPP identity from the token (`azp`, else `client_id`); `x-fapi-financial-id` may only repeat it (else 403); monolith trusted the header and fell back to `UNKNOWN_TPP` | anyone could read or decide another TPP's request | `TppIdentityTest`, `RequestToPayServiceIT.anotherTppCannotReadOrDecideTheRequest` |
| 3 | Ownership mismatch 400 -> 403 | correct semantics; consistent with the other extracted services | `PayRequestExceptionHandlerTest` |
| 4 | DPoP optional (monolith required a non-blank header but never verified it); when sent it is fully verified, and a DPoP-bound token without proof is 401 | platform contract: DPoP not required for payments services | `PayRequestControllerDPoPIntegrationTest`, `DPoPValidationServiceRejectionTest` |
| 5 | `X-Idempotency-Key` required on create (as on this repository's `main`); a retry with the same key and payload returns the first pay request instead of 409, different payload 409 | contracts skill: mutating endpoints replay the first result | `PayRequestServiceTest`, `RequestToPayServiceIT` (including an 8-thread race) |
| 6 | `X-FAPI-Interaction-ID` must match `^[A-Za-z0-9._:-]{1,128}$` | it becomes the event correlationId and a log field | `RequestToPayServiceIT.interactionIdMustBeASafeToken` |
| 7 | Amount: ISO 4217 currency and no more decimals than its minor unit (AED 500.105 -> 400); stored and published at the minor unit (`500` -> `"500.00"`) | Money value object; DB `NUMERIC(19,4)` | `MoneyTest`, `PayRequestLifecycleTest` |
| 8 | Data survives restarts and is shared by replicas (PostgreSQL); monolith kept pay requests in memory | own database | `RequestToPayServiceIT` |
| 9 | Events `evt.pay.rtp.{created,accepted,rejected}.v1` with the standard envelope through an outbox; the monolith published nothing (no-op adapter) and the seed's `rtp.pay_requests.v1` publisher never ran | catalog contract | `PayRequestEventEnvelopeFactoryTest`, `OutboxRelayTest` |
| 10 | Status reads cached per replica for 10 s (monolith: 60 s in one process); the deciding replica refreshes immediately | multi-replica | `PayRequestServiceTest` |
| 11 | Unknown paths 401/403 instead of 404 (deny by default) | security | `PayRequestControllerDPoPIntegrationTest` |
