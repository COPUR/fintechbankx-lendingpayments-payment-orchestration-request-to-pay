# RUNBOOK-EXTRACT-pay-request-to-pay

Status: **Proposed**. Extraction of the request-to-pay capability
(`open-finance-context`, package `requesttopay`) from
`enterprise-loan-management-system` into `svc-pay-request-to-pay` (this
repository), following the strangler-fig steps of `fbx-monolith-extraction` and
the template `docs/runbooks/RUNBOOK-EXTRACT-service-cutover.md` (adr-runbooks).
Nothing below has been executed.

## Document Control

- Runbook ID: `RUNBOOK-EXTRACT-pay-request-to-pay`
- Version: `v1.1` (Proposed)
- Owner squad: payments squad (owner of `fintechbankx-lendingpayments-payment-orchestration-request-to-pay`)
- Change window: to be set by the owner squad (not before the cross-repository order in section 2 is complete)
- Risk tier: **High** (proposed): payments context, TPP-facing API; an `Accepted` event may later drive payment
  initiation (ADR-030 open question 1). High risk needs Architecture Board approval (template precondition 8)
- Cut-over switch: ingress gateway routes in section 3 (the monolith has no internal caller of `requesttopay`, so
  there is no in-monolith adapter or flag); TPP cohort allow-list `rtp-cutover-cohort` at the gateway
- Rollback tags: monolith `rtp-extract-pre-cutover` on `master` (to create before step 5), target
  `svc-pay-request-to-pay-<version>` of the deployed release
- Matrix row: **LP-10**; regression mapping `docs/migration/REGRESSION_MAPPING.md`

| Field | Value |
|---|---|
| Context / service | `pay` / `svc-pay-request-to-pay` (Helm, Docker and service account name `payment-request-to-pay-service`, namespace `payments`) |
| Slice | PayRequest aggregate: create, read status, accept (consume with a payment id), reject |
| Owned data | `db_pay_request_to_pay_<env>`, schema `sc_pay_request_to_pay`: `pay_request`, `pay_request_idempotency`, `dpop_proof_jti`, `outbox_event` |
| Events | `evt.pay.rtp.{created,accepted,rejected}.v1`, types `Payments.PayRequest.{Created,Accepted,Rejected}.v1` ([AsyncAPI](../../api/asyncapi/svc-pay-request-to-pay.yaml)). No DLQ here: dead-letter topics belong to consumers (ADR-019, ADR-024) |
| Public paths (exact) | `POST /open-finance/v1/par`; `GET /open-finance/v1/payment-consents/{id}`; `POST /open-finance/v1/payment-consents/{id}/accept`; `POST /open-finance/v1/payment-consents/{id}/reject`. Nothing else, no prefix routes |
| Ids | this service mints `CONS-RTP2-<uuid>`; the monolith mints `CONS-RTP-<uuid>`. Follow-up calls are routed by this prefix in every phase |
| Depends on | Keycloak realm `fintechbankx` only (no synchronous calls to other services) |

## 1. Data ownership split

| Monolith object | Finding | Consequence |
|---|---|---|
| `open-finance-context/.../requesttopay/infrastructure/persistence/InMemoryPayRequestRepositoryAdapter` | the only repository adapter for pay requests in the monolith | pay requests never reached a database |
| Monolith Flyway migrations (`src/main/resources/db/migration`, `open-finance-context/.../db/migration/openfinance/V1__create_outbox.sql`, `V2__create_payment_eventing_support.sql`) | no table for pay requests | nothing to export |
| Monolith idempotency / cache for request-to-pay | in-memory | nothing to export |

**No backfill is needed** (template steps 4 to 6 do not apply). Cut-over is routing only, and this repository has
no `db/backfill` or data-split CI job. Pay requests that are open in the monolith when traffic moves stay there;
they live only in that process's memory and expire with it, as they do today on every restart.

Flyway migrations: `open-finance-infrastructure/src/main/resources/db/migration/V1__create_pay_request_tables.sql`,
`V2__create_outbox.sql`, `V3__outbox_failure_policy.sql`, `V4__outbox_park_counted.sql`. V1 replaces the seed
migration `V1__Create_pay_requests_table.sql` (schema `pis`), which no environment ever applied because the seed had
no runnable application. The service never reads monolith tables and no other service reads `sc_pay_request_to_pay`.

## 2. Preconditions

| Precondition | Owner |
|---|---|
| ADR-030 (`docs/architecture/decisions/ADR-local-rtp-openapi-realignment.md`) accepted and this PR merged | payments owner, API governance |
| Payments owner has answered ADR-030 open question 1 (who may accept or reject). Until then `OUTBOX_RELAY_ENABLED` stays `false` in every environment, so no consumer acts on `Accepted` | payments owner |
| Keycloak: confidential client `svc-pay-request-to-pay`; Audience mapper adding `svc-pay-request-to-pay` to every TPP client allowed to call it; client scope `payments` on those TPP clients (ADR-030 open question 2); optionally a mapper emitting `fbx_client_type` from the client attribute `fbx.client-type` (the service then requires `open-finance-tpp`); TPP clients DPoP-enabled | identity (fintechbankx-platform-identity-keycloak-ldap) |
| Mesh contract for `payment-request-to-pay-service` declares `datastores: [aurora-postgresql, msk]` (egress to the Aurora writer and reader on 5432 and the MSK IAM brokers on 9098, plus regional STS for IRSA); without it the readiness group (`db`) fails under `REGISTRY_ONLY` | fintechbankx-platform-mesh-security-service-mesh |
| Mesh: gateway routes for the four exact paths in section 3, with the platform forwarded-header rules (the DPoP `htu` check uses `X-Forwarded-Proto/Host/Port`) and the cohort allow-list; inbound ALLOW for `cluster.local/ns/istio-ingress/sa/istio-ingressgateway` to `payment-request-to-pay-service` on 8080 only (8081 is never public) | fintechbankx-platform-mesh-security-service-mesh |
| Topics `evt.pay.rtp.{created,accepted,rejected}.v1` in the topic catalog and on MSK (RF 3, `min.insync.replicas=2`); topic-scoped MSK IAM policy for this service's IRSA role (write on the three topics only) | fintechbankx-platform-event-streaming-kafka |
| openapi-catalog mirrors `api/openapi/request-to-pay-service.yaml` 1.0.0; asyncapi-catalog mirrors `api/asyncapi/svc-pay-request-to-pay.yaml` with the publishes status from this branch (and drops its dual-publish note) | API governance |
| Enterprise-architecture: LP-10 note updated (service runnable, events through the outbox, legacy `rtp.pay_requests.v1` publisher removed) | enterprise architecture |
| Regression parity: LP-10 run with 0 regressions, every difference one of LP-10-D01 to D13, run id `<run>-<sha7>` recorded (`docs/migration/REGRESSION_MAPPING.md`) | regression workstream, payments squad |
| Observability gate (section 4) passed | payments squad, platform observability |
| DBA bootstrap: create role `payment_request_to_pay_app` (owner of schema `sc_pay_request_to_pay`), write `{"username","password"}` to `<env>/payment-request-to-pay-service/db-app` | payments squad DBA |

Cross-repository order (each step waits for the previous one to merge):

1. ADR-030 accepted and this PR merged.
2. openapi-catalog mirror of the spec, version 1.0.0.
3. asyncapi-catalog: status publishes; drop the dual-publish note.
4. enterprise-architecture: LP-10 note.
5. event streaming: topics and the MSK IAM policy.
6. service mesh: mesh contract `datastores` and the gateway routes.
7. Cut-over (section 3).

## 3. Cutover plan

Gateway rules, evaluated in this order in every phase (regular expressions are anchored; no prefix matches):

| Rule | Match | Destination |
|---|---|---|
| R1 follow-ups to this service | `GET ^/open-finance/v1/payment-consents/CONS-RTP2-[0-9a-f-]{36}$`, `POST ^/open-finance/v1/payment-consents/CONS-RTP2-[0-9a-f-]{36}/(accept\|reject)$` | this service, **always**, including after a rollback (it alone holds those requests) |
| R2 follow-ups to the monolith | `GET` / `POST` on the same three paths with any other id | monolith |
| R3 create | `POST ^/open-finance/v1/par$` with the token's `azp` in `rtp-cutover-cohort` | this service |
| R4 create, everyone else | `POST ^/open-finance/v1/par$` | monolith |

Phases move only R3's cohort. A TPP is in one cohort at a time, so its create retries (same `X-Idempotency-Key`) reach
one backend; move a TPP only after telling it that keys sent before the move are not replayed by the other backend.

| Step | Action | Rollback | Rollback trigger (any one, over 15 minutes) |
|---|---|---|---|
| 1 | Deploy dark with `OUTBOX_RELAY_ENABLED=false` (DBA bootstrap and migrations per the platform runbook); smoke test with a test TPP: readiness UP, create / read / accept / reject over R1 and R3 | uninstall the chart; drop the schema | smoke test fails |
| 2 | Apply R1 to R4 with an empty cohort (no TPP traffic moves); check R2/R4 still reach the monolith | remove R1 to R4 | any 404 on the four paths that did not occur before |
| 3 | Canary: add one pilot TPP client to `rtp-cutover-cohort`; after 48 h clean, add TPPs in batches to about 10 %, then 50 %, then all, 48 h clean at each | remove every TPP from the cohort (R3 empty); R1 keeps serving `CONS-RTP2-` requests here | 5xx rate on the four paths above 1 %; p99 above 1 s; 404 rate on `/payment-consents/*` above its pre-cutover baseline (a misrouted follow-up); any idempotent-replay miss (the same TPP and `X-Idempotency-Key` hash seen by both backends within 24 h, gateway access log); 401 `invalid_dpop_proof` or 403 above 5 % of a TPP's calls (TPP not ready: remove that TPP only) |
| 4 | Soak: all TPPs in the cohort for **two weeks** at 100 % with every trigger clean; relay still off | as step 3 | as step 3 |
| 5 | After the soak and once the payments owner has answered ADR-030 open question 1: tag the monolith (`rtp-extract-pre-cutover`), then `OUTBOX_RELAY_ENABLED=true`; events written since step 3 are relayed in order. Watch `outbox_pending_events` drain | relay off; unsent events wait in the outbox (published events cannot be recalled) | the platform alert `OutboxEventsParked` fires for this service; `outbox_parked_rows` above 0; `outbox_oldest_pending_age_seconds` above 300 for 10 minutes; `outbox_relay_consecutive_failed_runs` above 5 |
| 6 | Remove the `requesttopay` package and controller from the monolith (follow-up PR in enterprise-loan-management-system); then delete R2 and R4 | revert that PR from the tag `rtp-extract-pre-cutover` and restore R2/R4 | monolith build or tests fail |

### Rollback during the canary and soak (steps 3 and 4)

The relay is off for the whole window, so no consumer learns of a pay request that a rollback would strand.

1. Empty `rtp-cutover-cohort`: new creates go to the monolith (R4).
2. Keep R1: follow-up calls for `CONS-RTP2-` ids keep reaching this service, which still serves them; R2 keeps
   monolith ids on the monolith. There is no data to move back: the monolith never persisted pay requests.
3. Re-run the smoke test on the monolith path; publish the incident and corrective actions within 24 h.

What a rollback leaves behind: pay requests created here stay here until they are decided or abandoned; their
outbox rows stay unsent until the relay is enabled (on a retried cut-over) or are discarded with the consumers'
owners' agreement.

## 4. Observability gate

1. Traces: `x-fapi-interaction-id` propagated from the gateway through the service to the outbox row
   (`correlationId`) and the Kafka record (`traceparent`).
2. Logs in the central sink; no PSU reference, creditor name or amount in labels or attributes.
3. Metrics baseline before step 3: request rate, 4xx/5xx and p99 per path; gateway 404 rate on the four paths.
4. Alerts. This chart ships no alert rules. Parked events are covered by the platform alert `OutboxEventsParked`
   (any increase of `outbox_parked_events_total` over 15 minutes, per `exception`, severity warning, routed by
   squad; `exception` is the payload error class or `OperatorPark`). The platform outbox rules also cover a
   stalled relay (`OutboxRelayStalled`, oldest pending event above 900 s) and send failures
   (`OutboxSendFailures`). Service-specific asks beyond those, for the observability team:
   - `outbox_parked_rows` above 0 (the authoritative signal: the gauge is read from the table, while the counter is
     best-effort, see section 5)
   - `outbox_oldest_pending_age_seconds` above 300 for 10 minutes (stricter than the platform's 900 s; only
     meaningful with the relay enabled)
   - `outbox_relay_consecutive_failed_runs` above 5
   - 5xx, p99, 404 and idempotent-replay-miss triggers of section 3

## 5. Parked outbox events

Policy: ADR-021 decision 4. There is no attempt cap and no time-based parking.

| Class | Errors | What the relay does | Signal |
|---|---|---|---|
| Payload | `RecordTooLargeException`, `SerializationException`, `InvalidTopicException` | parks the row at once (`park_reason` = `payload error (relay)`), continues with other pay requests; that pay request's later events wait | `outbox_parked_events_total{exception="<class>"}` increases; `outbox_parked_rows` above 0 |
| Everything else | broker timeouts and other retriable errors, `UnknownTopicOrPartitionException` (topic missing), the relay's send timeout, `SaslAuthenticationException`, `TopicAuthorizationException`, producer construction, anything unclassified | never parks: stops the run without marking any row, backs off 1 s doubling to 60 s, resumes by itself once the cause is fixed | `outbox_send_failures_total{exception="<class>"}`, `outbox_relay_consecutive_failed_runs`, `outbox_oldest_pending_age_seconds` |

Find parked rows:

```sql
SELECT event_id, created_seq, topic, aggregate_id, attempts, park_reason, last_error, parked_at
FROM sc_pay_request_to_pay.outbox_event
WHERE status = 'PARKED'
ORDER BY created_seq;
```

Replay after fixing the cause (as the schema owner):

```sql
UPDATE sc_pay_request_to_pay.outbox_event
SET status = 'PENDING', parked_at = NULL, park_reason = NULL, park_counted = false, attempts = 0, last_error = NULL
WHERE event_id = '<event id>';
```

The replayed row goes out on the next run, followed by its pay request's waiting events in `created_seq` order. To
discard a parked event instead (only with the consumers' owners' agreement), delete the row; the later events then
flow. Consumers de-duplicate on `eventId`.

**Operator park** (the only way a row that is not a payload error gets parked, for example a head row blocking the
queue while a fix is prepared). The reason is mandatory: the check constraint `ck_outbox_parked` refuses a park
without `parked_at` and `park_reason`.

```sql
UPDATE sc_pay_request_to_pay.outbox_event
SET status = 'PARKED', parked_at = now(), park_reason = 'operator: <ticket> <why>'
WHERE event_id = '<event id>';
```

The relay counts each operator park once on its next run (`outbox_parked_events_total{exception="OperatorPark"}`,
column `park_counted`, V4). Its pay request's later events wait until it is replayed as above.

Counting is best-effort: the relay marks `park_counted` in its batch transaction and increments the counter after
that commits, so a crash in between loses one increment rather than counting a park twice, and the counter restarts
at zero with the process. Decide what is parked from the gauge `outbox_parked_rows` (read from the table) or the
query above, never from the counter.

Evidence retention: published outbox rows are purged after 7 days (`requesttopay.outbox.retention: P7D`). Export
the rows behind any incident (event ids, `park_reason`, `last_error`) to the incident record before then.

## 6. Acceptance checklist

No box is ticked without a CI run linked in section 7. Local runs are noted but do not tick a box.

- [ ] Service builds and tests standalone (`./gradlew check` with PostgreSQL integration tests). Local only so far; CI run: to link
- [ ] Own schema and migrations; Hibernate validates the entities at startup. Local IT only; CI run: to link
- [ ] Events written through a transactional outbox with one active relay and no transaction across sends; payload errors parked and their pay request held back; every other error stops and backs off (ADR-021 decision 4). Local tests only; CI run: to link
- [ ] Idempotent create (`X-Idempotency-Key`, unique per TPP in the database, concurrent race and expired key tested). Local IT only; CI run: to link
- [ ] Concurrent accept and reject serialised (row lock and optimistic version); repeated decisions idempotent. Local IT only; CI run: to link
- [ ] Token audience and `payments` scope validated; TPP client required; TPP identity from the token; DPoP with `ath` on every TPP path. Local tests only; CI run: to link
- [ ] Container image, Helm chart and Terraform checked in CI (`Deployability` workflow). CI run: to link
- [ ] OpenAPI aligned with the controller and the oasdiff gate green with the waiver. CI run: to link
- [ ] ADR-030 accepted (OpenAPI breaking changes against `main`; delete the waiver in the next PR)
- [ ] Payments owner decision on who may accept (ADR-030 open question 1)
- [ ] Keycloak audience mapper and `payments` scope in place
- [ ] Mesh contract `datastores` and gateway routes R1 to R4 in place
- [ ] Topics created on the platform cluster and MSK IAM policy applied
- [ ] LP-10 parity run: 0 regressions, accepted differences referenced (run id: ...)
- [ ] Observability gate passed
- [ ] Two-week soak at 100 % clean; relay enabled after it
- [ ] Monolith tag `rtp-extract-pre-cutover` created; monolith `requesttopay` package removed

## 7. Evidence links

- PR links: this repository's extraction PR; catalog, enterprise-architecture, event-streaming and mesh PRs of section 2 (to add)
- Pipeline runs: (to add; none linked yet)
- Parity run: LP-10 `<run>-<sha7>` (to add)
- Backfill and verification reports: not applicable (no monolith data)
- Shadow diff report: not applicable (cohort canary instead)
- Dashboard snapshots: (to add per canary step)
- Incident / rollback references: (to add)
