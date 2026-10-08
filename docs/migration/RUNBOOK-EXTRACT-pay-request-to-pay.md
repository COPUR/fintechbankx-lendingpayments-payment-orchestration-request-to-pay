# RUNBOOK-EXTRACT-pay-request-to-pay

Status: **Proposed**. Extraction of the request-to-pay capability
(`open-finance-context`, package `requesttopay`) from
`enterprise-loan-management-system` into `svc-pay-request-to-pay` (this
repository), following the strangler-fig steps of `fbx-monolith-extraction`.

| Field | Value |
|---|---|
| Context / service | `pay` / `svc-pay-request-to-pay` (Helm, Docker and service account name `payment-request-to-pay-service`, namespace `payments`) |
| Slice | PayRequest aggregate: create, read status, accept (consume with a payment id), reject |
| Owned data | `db_pay_request_to_pay_<env>`, schema `sc_pay_request_to_pay`: `pay_request`, `pay_request_idempotency`, `dpop_proof_jti`, `outbox_event` |
| Events | `evt.pay.rtp.{created,accepted,rejected}.v1`, types `Payments.PayRequest.{Created,Accepted,Rejected}.v1` ([AsyncAPI](../../api/asyncapi/svc-pay-request-to-pay.yaml)); DLQ `evt.pay.rtp.dlq.v1` is written by consumers, not by this service |
| Depends on | Keycloak realm `fintechbankx` only (no synchronous calls to other services) |

## 1. Data ownership split

| Monolith object | Finding | Consequence |
|---|---|---|
| `open-finance-context/.../requesttopay/infrastructure/persistence/InMemoryPayRequestRepositoryAdapter` | the only repository adapter for pay requests in the monolith | pay requests never reached a database |
| Monolith Flyway migrations (`src/main/resources/db/migration`, `open-finance-context/.../db/migration/openfinance/V1__create_outbox.sql`, `V2__create_payment_eventing_support.sql`) | no table for pay requests | nothing to export |
| Monolith idempotency / cache for request-to-pay | in-memory | nothing to export |

**No backfill is needed.** Cut-over is routing only, and this repository has no
`db/backfill` or data-split CI job. Pay requests that are open in the monolith
when traffic moves stay there; they live only in that process's memory and
expire with it, as they do today on every restart.

Flyway migrations: `open-finance-infrastructure/src/main/resources/db/migration/V1__create_pay_request_tables.sql`,
`V2__create_outbox.sql`. V1 replaces the seed migration `V1__Create_pay_requests_table.sql` (schema `pis`), which no
environment ever applied because the seed had no runnable application. The service never reads monolith tables and no
other service reads `sc_pay_request_to_pay`.

## 2. Preconditions

| Precondition | Owner |
|---|---|
| Keycloak: confidential client `svc-pay-request-to-pay`; Audience mapper adding `svc-pay-request-to-pay` to every TPP client allowed to call it | identity (fintechbankx-platform-identity-keycloak-ldap) |
| Mesh: ALLOW rule for `cluster.local/ns/istio-ingress/sa/istio-ingressgateway` to `payment-request-to-pay-service` in `payments` | fintechbankx-platform-service-mesh-security |
| Topics `evt.pay.rtp.{created,accepted,rejected,dlq}.v1` in the topic catalog and on MSK | fintechbankx-platform-event-streaming-kafka |
| Asyncapi catalog mirrors `api/asyncapi/svc-pay-request-to-pay.yaml` (catalog PR pending, opened by the parent session) | API governance |
| DBA bootstrap: create role `payment_request_to_pay_app` (owner of schema `sc_pay_request_to_pay`), write `{"username","password"}` to `<env>/payment-request-to-pay-service/db-app` | payments squad DBA |

## 3. Cutover plan

| Step | Action | Rollback | Rollback trigger |
|---|---|---|---|
| 1 | `terraform apply`; DBA bootstrap; deploy with `OUTBOX_RELAY_ENABLED=false`; smoke test (readiness UP, create/read/accept with a test TPP) | `helm uninstall`; drop schema | smoke test fails |
| 2 | Create topics; set `OUTBOX_RELAY_ENABLED=true`; check `outbox_pending_events` drains to 0 and `outbox_parked_events` stays 0 | relay off; events wait in the outbox | `outbox_parked_events` > 0 or `outbox_oldest_pending_age_seconds` > 300 for 10 minutes |
| 3 | Ingress: route the request-to-pay API to this service (canary 10 %, then 100 %) | route back to the monolith; requests created here stay here and are still served by this service | 5xx rate above 1 % or p99 above 1 s for 10 minutes at any canary step |
| 4 | Monolith: remove the `requesttopay` package and its controller (follow-up PR in enterprise-loan-management-system) | revert that PR | n/a |

Rollback after step 3 is a forward fix: the monolith keeps no state to roll
back to, so there is nothing to replay.

## 4. Acceptance checklist

- [x] Service builds and tests standalone (`./gradlew check` with PostgreSQL integration tests)
- [x] Own schema and migrations; Hibernate validates the entities at startup
- [x] Events written through a transactional outbox, relayed in order with one active relay; poison rows parked after max attempts
- [x] Idempotent create (`x-idempotency-key`, unique per TPP in the database, concurrent race tested)
- [x] Concurrent accept and reject serialised (row lock and optimistic version)
- [x] Token audience validated; TPP identity taken from the token; DPoP required on every TPP path (`/open-finance/v1`)
- [x] Container image, Helm chart and Terraform checked in CI (`Deployability` workflow)
- [ ] Topics created on the platform cluster
- [ ] Keycloak audience mapper and mesh ALLOW rule in place
- [x] OpenAPI aligned with the controller (monolith paths under `/open-finance/v1`)
- [ ] Contract owner accepts the OpenAPI breaking changes against `main` (oasdiff: removed never-served `/par/{requestId}` paths, new required `PsuId`/`InstructedAmount`, interaction-id pattern)
- [ ] Monolith `requesttopay` package removed
