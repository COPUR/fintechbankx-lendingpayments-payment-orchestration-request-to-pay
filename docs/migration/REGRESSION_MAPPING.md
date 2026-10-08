# Regression mapping: monolith request-to-pay -> svc-pay-request-to-pay

Status: **Proposed**. Source: `enterprise-loan-management-system` (branch master),
`open-finance-context/open-finance-infrastructure/.../requesttopay/infrastructure/rest/PayRequestController.java`
(`@RequestMapping("/open-finance/v1")`). Target: this repository,
`open-finance-infrastructure/.../requesttopay/infrastructure/rest/PayRequestController.java`
(`@RequestMapping("/open-finance/v1")`). The extraction seed on `main` had moved the endpoints to
`/api/v1/pay-requests`; this branch restores the monolith's TPP paths (platform contract: TPP-facing endpoints live
under `/open-finance/v1` and require DPoP; `/api/v1` is for internal callers, and request-to-pay has none).

## Endpoints

| Monolith | New service | Request / response | Status codes |
|---|---|---|---|
| `POST /open-finance/v1/par` | `POST /open-finance/v1/par` (same path) | Same body (`Data.PsuId`, `Data.CreditorName`, `Data.InstructedAmount.{Amount,Currency}`) and response (`Data.ConsentId`, `Data.Status`, `Links.Self`); `Location`/`Links.Self` `/open-finance/v1/payment-consents/{id}` as in the monolith. New response header `X-Idempotent-Replay`. | 201 unchanged; new 409 `IDEMPOTENCY_KEY_REUSED` (same key, other payload); 400 on missing `X-Idempotency-Key`, invalid amount format or currency |
| `GET /open-finance/v1/payment-consents/{consentId}` | same path | Same body (`Data.ConsentId`, `Data.Status`, `Data.PaymentId`), `ETag` / `If-None-Match` -> 304 unchanged | 200/304/404 unchanged; other TPP 400 -> **403** |
| `POST /open-finance/v1/payment-consents/{consentId}/accept` | same path | Same body `{"paymentId"}` and response | 201 unchanged; finalized 400 `REQUEST_FINALIZED` unchanged; other TPP 400 -> **403**; concurrent change 409 `CONCURRENT_UPDATE` |
| `POST /open-finance/v1/payment-consents/{consentId}/reject` | same path | Same body and response | 200 unchanged; finalized 400; other TPP 400 -> **403** |
| `api/openapi/request-to-pay-service.yaml` on `main`: `/par/{requestId}[/accept|/reject]`, `DebtorIdentifier`, `RequestId` | spec rewritten to the served paths and fields (version 1.0.0) | the `main` spec was never served by the monolith or this repository | oasdiff reports 6 breaking errors against `main`; contract owner must accept them (ADR) |

## Intentional behaviour changes

| # | Change | Why | Test |
|---|---|---|---|
| 1 | Token required and validated (issuer, signature, `aud` contains `svc-pay-request-to-pay`); monolith only checked that `Authorization` and `DPoP` headers were non-blank | platform contract (Keycloak audience) | `AudienceValidatorTest`, `PayRequestControllerDPoPIntegrationTest` |
| 2 | TPP identity from the token (`azp`, else `client_id`); `x-fapi-financial-id` may only repeat it (else 403); monolith trusted the header and fell back to `UNKNOWN_TPP` | anyone could read or decide another TPP's request | `TppIdentityTest`, `RequestToPayServiceIT.anotherTppCannotReadOrDecideTheRequest` |
| 3 | Ownership mismatch 400 -> 403 | correct semantics; consistent with the other extracted services | `PayRequestExceptionHandlerTest` |
| 4 | DPoP required on every TPP path: `Authorization: DPoP`, a proof (signature, htm, htu, iat, jti unique across replicas), `cnf.jkt` equal to the proof key; Bearer, missing proof, unbound token or replayed proof -> 401 with `WWW-Authenticate: DPoP`. Monolith only checked the headers were non-blank | platform contract "DPoP applies by caller, not by namespace" | `PayRequestControllerDPoPIntegrationTest`, `DPoPRequestVerifierTest`, `RequestToPayServiceIT.bearerSchemeOnTheTppPathIsUnauthorizedAndStoresNothing`, `.aReplayedProofIsUnauthorized` |
| 5 | `X-Idempotency-Key` required on create (as on this repository's `main`); a retry with the same key and payload returns the first pay request instead of 409, different payload 409 | contracts skill: mutating endpoints replay the first result | `PayRequestServiceTest`, `RequestToPayServiceIT` (including an 8-thread race) |
| 6 | `X-FAPI-Interaction-ID` must match `^[A-Za-z0-9._:-]{1,128}$` | it becomes the event correlationId and a log field | `RequestToPayServiceIT.interactionIdMustBeASafeToken` |
| 7 | Amount: ISO 4217 currency and no more decimals than its minor unit (AED 500.105 -> 400); stored and published at the minor unit (`500` -> `"500.00"`) | Money value object; DB `NUMERIC(19,4)` | `MoneyTest`, `PayRequestLifecycleTest` |
| 8 | Data survives restarts and is shared by replicas (PostgreSQL); monolith kept pay requests in memory | own database | `RequestToPayServiceIT` |
| 9 | Events `evt.pay.rtp.{created,accepted,rejected}.v1` with the standard envelope through an outbox; the monolith published nothing (no-op adapter) and the seed's `rtp.pay_requests.v1` publisher never ran | catalog contract | `PayRequestEventEnvelopeFactoryTest`, `OutboxRelayTest` |
| 10 | Status reads cached per replica for 10 s (monolith: 60 s in one process); the deciding replica refreshes immediately | multi-replica | `PayRequestServiceTest` |
| 11 | Unknown paths 401/403 instead of 404 (deny by default); no internal `/api/v1` surface (no caller exists) | security | `PayRequestControllerDPoPIntegrationTest.oldInternalPrefixAndUnknownPathsAreDenied` |
