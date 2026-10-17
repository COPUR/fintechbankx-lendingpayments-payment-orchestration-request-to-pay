# Regression mapping: monolith request-to-pay -> svc-pay-request-to-pay

- Status: **Proposed**
- Matrix row: **LP-10** (`docs/alignment/monolith-to-repo-alignment.csv` in the enterprise-architecture repository;
  status `partial`, `parity` empty: no parity run has been recorded)
- Rules: ADR-029 section 2 (scenarios keyed by matrix row, same input to both systems, accepted differences listed
  with the decision that allows them, any other difference fails the run)
- Monolith: `enterprise-loan-management-system`, branch `master` (pin the commit in each parity run),
  `open-finance-context/open-finance-infrastructure/.../requesttopay/infrastructure/rest/PayRequestController.java`
  (`@RequestMapping("/open-finance/v1")`)
- Service: this repository,
  `open-finance-infrastructure/.../requesttopay/infrastructure/rest/PayRequestController.java`
  (`@RequestMapping("/open-finance/v1")`)

The extraction seed on `main` had moved the endpoints to `/api/v1/pay-requests`; this branch restores the
monolith's TPP paths (platform contract: TPP-facing endpoints live under `/open-finance/v1` and require DPoP;
`/api/v1` is for internal callers, and request-to-pay has none). No path mapping is needed between the systems.

## Parity gate (blocks cut-over)

| Item | Value |
|---|---|
| Scenarios | LP-10-S01 to LP-10-S04 below, against the monolith at a pinned `master` commit and against this service's release candidate, same seed data and same TPP |
| Normalisation | ignore generated ids (`Data.ConsentId`, including the prefix, see LP-10-D11), timestamps, `ETag` values and trace headers; compare `InstructedAmount` exactly (amount, scale, currency) |
| Pass condition | 0 regressions on LP-10; every difference observed is one of LP-10-D01 to LP-10-D13 |
| Run id | `<run>-<sha7>` of the tested revision, recorded in the LP-10 `parity` column and in the runbook's evidence links |
| Owner | harness and catalog: "Regression tests against the monolith" workstream (ADR-029 2.6); scenarios and fixes: payments squad |
| Current state | **not run.** No harness run exists for LP-10; cut-over (runbook step 5) does not start until it passes |

Each scenario runs the monolith's request shape where it is still valid for the service. Where an accepted
difference changes the request (token with `payments` scope and DPoP `ath`, `X-Idempotency-Key`, opaque `PsuId`),
the service side sends the adjusted request and the comparison covers the response and state only.

## Scenarios

| Scenario | Monolith | Service | Request / response compared | Status codes compared |
|---|---|---|---|---|
| LP-10-S01 create | `POST /open-finance/v1/par` | same path | body `Data.PsuId`, `Data.CreditorName`, `Data.InstructedAmount.{Amount,Currency}`; response `Data.ConsentId`, `Data.Status`, `Links.Self`; `Location` / `Links.Self` = `/open-finance/v1/payment-consents/{id}` | 201; 400 on invalid amount or currency |
| LP-10-S02 read status | `GET /open-finance/v1/payment-consents/{consentId}` | same path | `Data.ConsentId`, `Data.Status`, `Data.PaymentId`; `If-None-Match` -> 304 | 200, 304, 404 (also for another TPP's request, LP-10-D04) |
| LP-10-S03 accept | `POST /open-finance/v1/payment-consents/{consentId}/accept` | same path | body `{"paymentId"}`; response; state read back through S02 | 201; 400 `REQUEST_FINALIZED` after a reject or with another paymentId; 404 (also for another TPP's request, LP-10-D04) |
| LP-10-S04 reject | `POST /open-finance/v1/payment-consents/{consentId}/reject` | same path | response; state read back through S02 | 200; 400 `REQUEST_FINALIZED` after an accept; 404 (also for another TPP's request, LP-10-D04) |

`IllegalStateException` (an internal invariant failure) is 500 `INTERNAL_ERROR` "Unexpected error occurred" in both
systems (monolith: generic handler; service: `PayRequestErrorResponsesOverHttpTest.anIllegalStateIsAnInternalErrorWithAFixedMessage`).
This is parity, not a difference.

## Accepted differences

"Decision" names what allows the difference and its status. Nothing here is accepted until the payments owner
approves it; ADR-030 is the governance number of `docs/architecture/decisions/ADR-local-rtp-openapi-realignment.md`
(Proposed).

| Id | Scenarios | Difference (monolith -> service) | Decision (status) | Test |
|---|---|---|---|---|
| LP-10-D01 | all | Token required and validated (issuer, signature, `aud` contains `svc-pay-request-to-pay`); the monolith only checked that `Authorization` and `DPoP` were non-blank | ADR-020 service tokens and the platform DPoP contract (Proposed) | `AudienceValidatorTest`, `PayRequestControllerDPoPIntegrationTest.requestWithoutTokenGetsADpopChallenge` |
| LP-10-D02 | all | The token must also carry scope `payments` and the claim `fbx_client_type` = `open-finance-tpp` (fail closed: absent or another value is refused); service clients (`svc-*`) and the first-party channels (`fintechbankx-web`, `fintechbankx-mobile`) are refused even with the claim. Otherwise 403 | ADR-030 open question 2 (Proposed); identity realm-as-code: optional client scope `payments` and default client scope `fbx-client-type-open-finance-tpp` on TPP clients (Proposed, not yet on the identity repo's main) | `PayRequestControllerDPoPIntegrationTest.tokenWithTheAudienceButWithoutThePaymentsScopeIsForbidden`, `.serviceClientTokenIsForbiddenEvenWithTheScope`, `.firstPartyChannelTokenIsForbidden`, `.clientTypeClaimOtherThanOpenFinanceTppIsForbidden`, `.aTokenWithoutTheClientTypeClaimIsForbiddenEvenForAKnownTppClient`, `.aTokenFromAnUnknownClientWithoutTheClientTypeClaimIsForbidden`, `.anyTppClientWithTheClaimAndThePaymentsScopeIsServed`, `TppClientPolicyTest` |
| LP-10-D03 | all | TPP identity from the token (`azp`, else `client_id`); `x-fapi-financial-id` may only repeat it (else 403); no token client -> 403. The monolith trusted the header and fell back to `UNKNOWN_TPP` | platform security contract (Proposed) | `TppIdentityTest`, `RequestToPayServiceIT.anotherTppCannotReadOrDecideTheRequest` |
| LP-10-D04 | S02, S03, S04 | Another TPP's request: 400 -> **404**, with the same body as an unknown id (code `NOT_FOUND`, message `Pay request not found`); the reason (`NOT_FOUND` or `OTHER_TPP`) is logged only, so a TPP cannot probe for other TPPs' ids. 403 stays for a TPP's own request that its token scope does not cover | ADR-025 item 5 (governance ruling, Proposed); ADR-030 (Proposed) | `RequestToPayServiceIT.anUnknownPayRequestAndAnotherTppsGetTheSame404Body`, `.anotherTppCannotReadOrDecideTheRequest`, `PayRequestServiceTest.shouldRejectWrongOwner`, `.shouldRejectPayRequestNotFound`, `.decisionByAnotherTppIsRefusedAndNothingIsSaved`, `PayRequestExceptionHandlerTest.unknownAndAnotherTppsPayRequestGetTheSameBody`, `PayRequestControllerDPoPIntegrationTest.readingAnotherTppsPayRequestIsNotFound` |
| LP-10-D05 | all | DPoP (RFC 9449) on every TPP path: `Authorization: DPoP`; a proof with valid signature, `htm`, `htu` (public URL), `iat` at most 300 s old and 60 s ahead, `ath` equal to the access token's hash, a `jti` used once across replicas (consumed only after the other checks pass); `cnf.jkt` equal to the proof key. Otherwise 401 with `WWW-Authenticate: DPoP` | platform DPoP contract (Proposed) | `DPoPProofBindingTest`, `PayRequestControllerDPoPIntegrationTest.proofBoundToAnotherAccessTokenIsUnauthorized`, `RequestToPayServiceIT.bearerSchemeOnTheTppPathIsUnauthorizedAndStoresNothing`, `.aReplayedProofIsUnauthorized` |
| LP-10-D06 | S01 | `X-Idempotency-Key` required; same key and payload replays the first pay request (`X-Idempotent-Replay: true`), other payload 409 `IDEMPOTENCY_KEY_REUSED`; an expired key (24 h) can be reused | contracts skill: mutating endpoints replay the first result (Proposed) | `PayRequestServiceTest`, `RequestToPayServiceIT.retryWithSameKeyReturnsTheFirstRequestAndDifferentPayloadIsAConflict`, `.concurrentCreatesWithTheSameKeyProduceOnePayRequest`, `.anExpiredKeyCanBeReusedForANewRequest` |
| LP-10-D07 | S01 | `Data.PsuId` must match `^[A-Za-z0-9-]{1,64}$` (an opaque PSU reference, not a name, e-mail or IBAN); the monolith took any non-blank text. Otherwise 400 `INVALID_REQUEST` | ADR-030 (Proposed); data classification of `debtorId` | `CreatePayRequestCommandTest.psuIdIsAnOpaqueReferenceOfLettersDigitsAndHyphens`, `PayRequestControllerDPoPIntegrationTest.psuIdThatIsNotAnOpaqueReferenceIsAnInvalidRequest` |
| LP-10-D08 | S01 | `X-FAPI-Interaction-ID` must match `^[A-Za-z0-9._:-]{1,128}$` | it becomes the event correlationId and a log field (Proposed) | `RequestToPayServiceIT.interactionIdMustBeASafeToken` |
| LP-10-D09 | S01 | Amount: ISO 4217 currency and no more decimals than its minor unit (AED 500.105 -> 400); stored and returned at the minor unit (`500` -> `"500.00"`) | Money value object, ADR-021 data rules (Proposed) | `MoneyTest`, `PayRequestLifecycleTest` |
| LP-10-D10 | S03, S04 | Repeating the decision already made (accept with the same paymentId, or reject again) returns the current state (201 / 200) and publishes nothing; the monolith answered 400 `REQUEST_FINALIZED`. The opposite decision, or another paymentId, is still 400 `REQUEST_FINALIZED`. Optional `reason` (at most 256 characters) on accept and reject | ADR-030 (Proposed): retries after a lost response must be safe | `PayRequestDecisionTest`, `RequestToPayServiceIT.aRepeatedDecisionReturnsTheCurrentStateAndPublishesNothingNew` |
| LP-10-D11 | S01 | Generated ids start with `CONS-RTP2-` (monolith `CONS-RTP-`); still opaque. Lets the gateway route follow-up calls to the backend that created the request in every cut-over phase | runbook routing (Proposed) | `RequestToPayConfigurationTest.idsAreDistinctFromTheMonolithsSoTheGatewayCanRouteByPrefix` |
| LP-10-D12 | all | Events `Payments.PayRequest.{Created,Accepted,Rejected}.v1` on the aggregate topic `evt.pay.rtp.v1` (ADR-019, one topic per aggregate; `eventType` header) with the standard envelope through a transactional outbox; decision events name the deciding client (`actorClientId`) and the optional `reason`. The monolith published nothing (no-op adapter); the seed's `rtp.pay_requests.v1` publisher never ran. Published only once the relay is enabled (runbook) | ADR-019, ADR-024, ADR-021 decision 4 (Proposed); AsyncAPI `api/asyncapi/svc-pay-request-to-pay.yaml` | `PayRequestEventEnvelopeFactoryTest`, `OutboxRelayTest`, `AsyncApiProviderContractTest`, `RequestToPayServiceIT.lifecycleOverHttpPersistsStateAndWritesEveryEventToTheOutbox` |
| LP-10-D13 | all | State persists and is shared by replicas (PostgreSQL; the monolith kept pay requests in memory); status reads cached per replica for 10 s (monolith 60 s in one process), the deciding replica refreshes at once; unknown paths and the old `/api/v1` prefix 401/403 instead of 404 (deny by default) | ADR-021 own database; deny-by-default security (Proposed) | `RequestToPayServiceIT`, `PayRequestServiceTest`, `PayRequestControllerDPoPIntegrationTest.oldInternalPrefixAndUnknownPathsAreDenied` |

## OpenAPI on `main`

`api/openapi/request-to-pay-service.yaml` on `main` (`/par/{requestId}[/accept|/reject]`, `DebtorIdentifier`,
`RequestId`) was never served by the monolith or by this repository. This branch rewrites it to the served paths and
fields (version 1.0.0): 6 oasdiff breaking errors against `main`, each waived in
`api/openapi/request-to-pay-service.accepted-breaking.txt` and listed in ADR-030 (Proposed). This is a contract
change, not a behaviour difference against the monolith, so it has no LP-10-D id.
