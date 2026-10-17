# Deployment and AWS Well-Architected mapping

Status: **Proposed**. How `svc-pay-request-to-pay` runs on AWS and which file
implements each Well-Architected concern. Claims here point at code; anything
not listed is not done yet.

## Runtime shape

```
TPP ─▶ Istio ingress ─▶ payment-request-to-pay-service pods (EKS, namespace payments, 3..12, HPA)
                           │  ├─ JDBC ─▶ Aurora PostgreSQL Serverless v2 (Multi-AZ), schema sc_pay_request_to_pay
                           │  └─ JWKS ─▶ Keycloak realm fintechbankx
                           └─ outbox relay ─▶ Amazon MSK (IAM auth) evt.pay.rtp.v1
```

| Artifact | Path |
|---|---|
| Image | `Dockerfile` (layered Spring Boot jar, JRE 23, uid 10001) |
| Kubernetes | `deploy/helm/payment-request-to-pay-service` (`values.yaml` prod-shaped, `values-dev.yaml`) |
| AWS | `deploy/terraform` (Aurora, KMS, Secrets Manager, IRSA with MSK policy, alarms; platform `microservice-base` module) |
| Runtime config | `open-finance-bootstrap/src/main/resources/application.yml`, `application-kafka-msk.yml`, `application-kafka-strimzi.yml` |
| CI proof | `.github/workflows/required-gates.yml`, `.github/workflows/deployability.yml` |

## Well-Architected pillars

| Pillar | What is in place | Where |
|---|---|---|
| Operational excellence | Liveness/readiness on the management port 8081; Prometheus metrics tagged `service`; outbox gauges `outbox_pending_events`, `outbox_parked_rows`, `outbox_oldest_pending_age_seconds`, counters `outbox_parked_events_total` and `outbox_send_failures_total` (tag `exception` = class name only; operator parks done with the runbook SQL are counted once by the relay as `OperatorPark`), gauge `outbox_relay_consecutive_failed_runs`; OTLP traces and W3C `traceparent` on every Kafka record; x-fapi-interaction-id as event correlationId; IaC for every AWS resource | `application.yml`, `OutboxConfiguration`, `OutboxPayRequestEventPublisher`, `OutboxRelay`, `deploy/terraform` |
| Security | Resource server with issuer and `aud` validation; TPP identity from the token (`azp`/`client_id`), header cannot override it, other TPPs get 403; DPoP required on every TPP path (`Authorization: DPoP`, proof signature, htm/htu/iat, jti replay across replicas, `cnf.jkt` binding; Bearer gets 401); deny-by-default paths; non-root, read-only root filesystem, capabilities dropped; DB credentials from Secrets Manager via External Secrets (`aws-secrets-manager`, keys only under `<env>/payment-request-to-pay-service/`); two DB roles (the pods hold a DML-only runtime role, the schema owner only the pre-install/pre-upgrade Flyway Job); `rds.force_ssl` and JDBC `sslmode=verify-full` against the RDS CA bundle mounted at `/etc/fintechbankx/rds-ca`; KMS-encrypted storage, snapshots and secrets with rotation; IRSA limited to MSK writes on the three own topics; NetworkPolicy owned by the mesh repo (the chart's is opt-in) | `SecurityConfig`, `TppIdentity`, `DPoPRequestVerifier`, `JdbcDPoPNonceRepository`, `deployment.yaml`, `migration-job.yaml`, `networkpolicy.yaml`, `externalsecret.yaml`, `V5__grant_runtime_role_least_privilege.sql`, `main.tf` |
| Reliability | Aurora Multi-AZ, 35-day PITR in prod, deletion protection; pods spread across zones, PDB, zero-unavailable rolling updates, graceful shutdown; transactional outbox, single active relay, per-aggregate ordering; payload errors park the row (its aggregate waits), every other send error stops the run and backs off without marking anything, and only an operator parks such a row, with a recorded reason (ADR-021 decision 4); idempotent create with a unique key per TPP; row lock plus optimistic version on decisions; database check constraints on status, currency and payment id | `main.tf`, `deployment.yaml`, `pdb.yaml`, `OutboxRelay`, `V3__outbox_failure_policy.sql`, `JdbcPayRequestIdempotencyAdapter`, `JpaPayRequestRepositoryAdapter`, `V1__create_pay_request_tables.sql` |
| Performance efficiency | Stateless pods scaled by HPA on CPU; Aurora Serverless v2 ACUs; virtual threads; short per-replica status cache; partial indexes for the outbox queue | `hpa.yaml`, `main.tf`, `application.yml`, `V2__create_outbox.sql` |
| Cost optimization | Serverless v2 floor of 0.5 ACU; dev overrides (single Aurora instance, 2-4 pods); published outbox rows purged after 7 days; expired idempotency keys and DPoP jti purged every 10 minutes; no Redis | `environments/dev.tfvars.example`, `values-dev.yaml`, `OutboxConfiguration` |
| Sustainability | Scale-down to the minimum footprint outside peak; layered image keeps rebuilds small | `hpa.yaml`, `Dockerfile` |

## Known gaps

- `docker build`, `helm lint/template`, `terraform init/validate` were not run in the authoring environment (no Docker daemon, Helm and the Terraform registry unreachable); the Deployability workflow runs them.
- Status reads are cached per replica; another replica can serve the previous status for up to `PAY_REQUEST_CACHE_TTL` (10 s).
- `msk-client-access` does not exist in the terraform-modules repository yet; the MSK policy is inline (TODO in `main.tf`).
- `microservice-base` is referenced at `ref=main`; pin a tag once the modules repo publishes releases.
- No load test yet; HPA targets are starting values.
