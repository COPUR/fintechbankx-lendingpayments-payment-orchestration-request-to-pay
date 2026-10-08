# ADR-local: realign the request-to-pay OpenAPI spec with the served TPP API

- Status: **Proposed** (the decision is the user's / API owner's; nothing here is accepted until they approve it)
- Number: governance numbers this decision **ADR-030** when it is accepted (this file keeps its local name until then)
- Date: 2026-10-08
- Service: `svc-pay-request-to-pay`
- Spec: `api/openapi/request-to-pay-service.yaml`
- Waiver: `api/openapi/request-to-pay-service.accepted-breaking.txt` (one change only, reviewed via CODEOWNERS)

## Context

The spec on `main` (version 0.1.0) described:

- `POST /par`, `GET /par/{requestId}`, `POST /par/{requestId}/accept`, `POST /par/{requestId}/reject`;
- a create body with `Data.DebtorIdentifier`, `Data.Amount`, `Data.Currency`, `Data.DueDate`;
- responses with `Data.RequestId`.

No deployed service ever served that contract:

- The monolith (`enterprise-loan-management-system`, `open-finance-context`, request-to-pay `PayRequestController`) served
  `POST /open-finance/v1/par` and `GET|POST /open-finance/v1/payment-consents/{consentId}[/accept|/reject]`,
  with body `Data.PsuId`, `Data.CreditorName`, `Data.InstructedAmount.{Amount,Currency}` and responses with
  `Data.ConsentId`, `Data.Status`, `Data.PaymentId`, `Links.Self`.
- The extraction seed on this repository's `main` served the same body and responses under `/api/v1/pay-requests`.
  The spec matched neither.

The platform contract ("DPoP applies by caller, not by namespace") puts TPP-facing endpoints under
`/open-finance/v1` and requires DPoP-bound tokens on them. Request-to-pay is called by TPPs only.

## Decision (proposed)

1. Serve the monolith's paths and payloads under `/open-finance/v1`. Drop `/api/v1/pay-requests`, because no
   internal caller exists.
2. Rewrite the spec to what is served (version 1.0.0): `dpopAuth` scheme, required `DPoP` header, the monolith's
   paths and fields, the error codes, and the interaction-id pattern the controller enforces. Drop
   `x-jws-signature`, which was never checked.
3. Accept the six breaking changes oasdiff reports against `main`, listed one per line in the waiver file:
   three removed `/par/{requestId}` paths, new required `Data/PsuId` and `Data/InstructedAmount`, and the
   pattern on `x-fapi-interaction-id`. The owner's acceptance covers the whole change set below, not only the six
   gated lines.
4. Keep the monolith's names (`/payment-consents/{consentId}`, `Data.ConsentId`) for TPP compatibility, and state
   in the spec, the AsyncAPI contract and here that the id is a **pay-request id local to `evt.pay.rtp`** and to
   this service. It is minted by svc-pay-request-to-pay and is **not** a consent of
   `svc-of-consent-authorization` (which owns the Consent aggregate, `evt.of.consent`): it cannot be looked up
   there and must not be correlated with consent events. The paths stay as they are until the user decides
   (see Alternatives considered).

## Full change set against `main` (oasdiff 1.12.1, `oasdiff breaking <main spec> <this spec>`)

Run against `main` at 00d8207: 18 changes, 6 errors and 12 warnings. Only the errors fail the gate; the waiver
file lists those six.

| Level | Change |
|---|---|
| error | POST /par added the new required request property 'Data/InstructedAmount' [new-required-request-property] |
| error | POST /par added the new required request property 'Data/PsuId' [new-required-request-property] |
| error | POST /par added the pattern '^[A-Za-z0-9._:-]{1,128}$' to the 'header' request parameter 'x-fapi-interaction-id' [request-parameter-pattern-added] |
| error | GET /par/{requestId} api path removed without deprecation [api-path-removed-without-deprecation] |
| error | POST /par/{requestId}/accept api path removed without deprecation [api-path-removed-without-deprecation] |
| error | POST /par/{requestId}/reject api path removed without deprecation [api-path-removed-without-deprecation] |
| warning | POST /par for the 'header' request parameter 'x-idempotency-key', the maxLength was set to '128' [request-parameter-max-length-set] |
| warning | POST /par deleted the 'header' request parameter 'x-jws-signature' [request-parameter-removed] |
| warning | POST /par the 'Data/CreditorName' request property's maxLength was set to '140' [request-property-max-length-set] |
| warning | POST /par removed the request property 'Data/Amount' [request-property-removed] |
| warning | POST /par removed the request property 'Data/Currency' [request-property-removed] |
| warning | POST /par removed the request property 'Data/DebtorIdentifier' [request-property-removed] |
| warning | POST /par removed the request property 'Data/DueDate' [request-property-removed] |
| warning | POST /par removed the optional property 'Data/PaymentId' from the response with the '201' status [response-optional-property-removed] |
| warning | POST /par removed the optional property 'Data/RequestId' from the response with the '201' status [response-optional-property-removed] |
| warning | POST /par added the new 'AwaitingAuthorisation' enum value to the 'Data/Status' response property for the response status '201' [response-property-enum-value-added] |
| warning | POST /par added the new 'Consumed' enum value to the 'Data/Status' response property for the response status '201' [response-property-enum-value-added] |
| warning | POST /par added the new 'Rejected' enum value to the 'Data/Status' response property for the response status '201' [response-property-enum-value-added] |

Changes oasdiff does not report, also covered by this decision:

- **Security scheme:** `main` declared an HTTP `bearer` scheme; this spec declares `dpopAuth` (HTTP `DPoP`
  scheme, DPoP proof with `ath`, `cnf.jkt` binding, `aud` and the `payments` scope). A Bearer token is now 401.
- **Idempotency on accept and reject:** `main` declared `x-idempotency-key` on accept and reject; this spec
  does not. Instead a repeated decision with the same outcome is idempotent by state (accept with the same
  paymentId on a Consumed request returns 201 with the current state, reject on a Rejected request returns 200,
  neither publishes an event).

## Consequences

- TPP clients built against the monolith keep their paths and payloads (see `docs/migration/REGRESSION_MAPPING.md`).
  Evidence of a real monolith request-to-pay client: none found in the repositories (in the workspace only the
  monolith's `PayRequestController` and its own UAT and integration tests reference these paths, and no TPP
  integration is recorded); whether a
  production TPP calls it is **unverified**. If none does, the compatibility argument is about the regression
  oracle, not about live clients.
- `DueDate` (request expiry) is removed: `main` declared `Data.DueDate`, the monolith never had it and this
  service does not expire pay requests. A request stays AwaitingAuthorisation until a decision. Follow-up for the
  payments owner: decide whether request-to-pay needs an expiry (status Expired, a scheduled sweep and an
  `evt.pay.rtp.expired.v1` event); adding it later is a minor change.
- A client that followed the `main` spec could never have worked against any deployment, so no working
  integration breaks.
- The waiver applies to this change only. `scripts/ci/oasdiff-breaking.sh` (ported from the OpenAPI catalog's
  fixed check) treats it as stale once it is on `main`: it is never applied again, the push run on `main` passes
  (the spec equals the base), and the next pull request that touches the spec or the waiver must delete it. Any
  other pull request gets a notice only.

## Alternatives considered

- **Keep the `main` spec and implement it.** Rejected: it differs from the monolith's TPP API, which the
  regression parity runs depend on.
- **Keep `/par/{requestId}` as deprecated.** Rejected: the spec would document paths nobody serves.
- **Publish under `/open-finance/v2`.** Rejected: there is no v1 client of the spec to protect.
- **Name the aggregate after itself: `/open-finance/v1/pay-requests/{payRequestId}`, `Data.PayRequestId`**, and keep
  `/payment-consents` only as a documented monolith-compatibility alias with a sunset date. Not chosen yet: the
  user decides. Pro: no collision with the Consent aggregate of svc-of-consent-authorization, and no prefix clash
  with bulk orchestration, whose spec on `main` claims `/open-finance/v1/payment-consents/{consentId}/file`
  (gateway routing by the `/payment-consents` prefix is ambiguous; exact-path rules are needed while both exist).
  Con: TPPs that follow the monolith's paths must move, and the regression oracle compares different paths.
- **Keep the monolith paths and document the id as local** (decision 4). Chosen for now as the reversible
  default; it costs nothing if the alias option is taken before the first TPP integrates.

## Open questions for the payments owner (block `OUTBOX_RELAY_ENABLED=true`)

1. **Who may accept or reject a pay request?** Today it is the TPP that created it, with a paymentId it supplies
   and nobody verifies (monolith parity). The Accepted event therefore says only "the requesting TPP reported
   acceptance with paymentId X". Options: require a PSU-bound token (customer_id equal to the stored debtor
   reference) or a first-party path called by the ASPSP after SCA; and have svc-pay-initiation-settlement confirm
   the payment (API check or its evt.pay.payment events) before Accepted is published. Until this is decided the
   relay stays off, so no consumer acts on Accepted.
2. **Payments scope:** the TPP paths require scope `payments` in addition to `aud`. The realm has no such client
   scope yet; identity must add it (or name the catalogued scope) before TPPs can call the service.
3. **Parked events:** a parked event keeps the later events of its pay request pending until an operator replays
   it (ADR-021 decision 4). Confirm this is wanted over publishing later events with a gap.

Reversibility: **reversible until the first TPP integrates, irreversible after.** The naming can still change
before the first TPP integrates (nothing serves or calls this spec yet); the version stays 1.0.0 (pre-release rule:
a spec stays 1.0.0 until it first lands on the catalog's main). Once a TPP integrates against `/open-finance/v1`,
any rename or removal needs a new major version with a migration window.
