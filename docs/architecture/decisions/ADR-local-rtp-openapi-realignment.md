# ADR-local: realign the request-to-pay OpenAPI spec with the served TPP API

- Status: **Proposed** (the decision is the user's / API owner's; nothing here is accepted until they approve it)
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
   pattern on `x-fapi-interaction-id`.

## Consequences

- TPP clients built against the monolith keep their paths and payloads (see `docs/migration/REGRESSION_MAPPING.md`).
- A client that followed the `main` spec could never have worked against any deployment, so no working
  integration breaks.
- The waiver file must be deleted in the first PR after this one merges. `scripts/ci/oasdiff-breaking.sh`
  enforces this.

## Alternatives considered

- **Keep the `main` spec and implement it.** Rejected: it differs from the monolith's TPP API, which the
  regression parity runs depend on.
- **Keep `/par/{requestId}` as deprecated.** Rejected: the spec would document paths nobody serves.
- **Publish under `/open-finance/v2`.** Rejected: there is no v1 client of the spec to protect.

Reversibility: reversible (spec and controller paths can be changed again). Once TPPs integrate against
`/open-finance/v1`, any later change needs a new version.
