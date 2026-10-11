# fintechbankx-lendingpayments-payment-orchestration-request-to-pay

Bu repository, FinTechBankX DDD/EDA dönüşümünde **svc-pay-request-to-pay** servis yetkinliğinin kaynak kodunu, kontratlarını ve operasyonel guardrail'lerini içerir.

## Sorumluluk ve Sahiplik
| Alan | Değer |
|---|---|
| Organizasyon Modeli | Spotify Model (Tribe/Squad) |
| Tribe | Lending & Payments Tribe |
| Squad | Recurring and Bulk Payments Squad |
| Repo Kümesi (Capability) | payments |
| Service ID | svc-pay-request-to-pay |
| Bounded Context | payment_request_to_pay |
| Wave | 3 |
| Mimari Yaklaşım | DDD + Hexagonal + Event-Driven |

## Sorumluluk Sınırları
- Bu repo kendi bounded context domain modelinin tek yetkili sahibidir.
- Domain kuralları altyapıdan bağımsız tutulur; entegrasyonlar port/adapter katmanında yönetilir.
- API/Event kontratları geriye dönük uyumluluk kontrolleri ile korunur.
- Güvenlik guardrail'leri (mTLS, token doğrulama, idempotency, log hijyeni) CI/CD ile zorlanır.

## Kapsam
### In Scope
- payment_request_to_pay bağlamına ait uygulama kodu, testler ve otomasyon.
- Bu servise ait OpenAPI/AsyncAPI veya şema artefaktları.
- Bu servisin çalışma zamanı operasyonları (gözlemlenebilirlik, release, rollback).

### Out of Scope
- Diğer bounded context'lerin iş kuralları ve veri sahipliği.
- Paylaşımlı DB anti-pattern'i; cross-context doğrudan tablo erişimi.
- Platform dışı gizli bilgi/anahtar yönetimi (merkezi policy dışında local hardcode).

## Mühendislik Standartları
- **TDD öncelikli** geliştirme, birim test + entegrasyon testi.
- **Clean Architecture**: Domain katmanı framework bağımsız.
- **12-Factor** ve environment-driven configuration.
- **FAPI odaklı güvenlik** (OIDC/OAuth2, mTLS, DPoP gereksinimleri ilgili servislerde).
- **PII güvenliği**: loglarda maskeleme, secret'ların source/env içine yazılmaması.

## Branching ve Release Akışı
- Uzun ömürlü branch'ler: `main`, `dev`, `staging`, `local`.
- Feature branch kuralı: `codex/<kisa-aciklama>`.
- Release yaklaşımı: PR + required status checks + tag tabanlı sürümleme.

## Run, test and deploy

Status: **Proposed** until the Recurring and Bulk Payments Squad merges and releases it.

| What | Command / path |
|---|---|
| Unit, architecture and integration tests | `./gradlew check` (PostgreSQL integration tests need `TEST_DB_URL`, `TEST_DB_USERNAME`, `TEST_DB_PASSWORD`; they skip locally without it and fail when `CI=true`) |
| Run locally | `SPRING_PROFILES_ACTIVE=local SPRING_DATASOURCE_PASSWORD=... ./gradlew :open-finance-bootstrap:bootRun` (PostgreSQL on localhost:5432, database `db_pay_request_to_pay_local`; the `local` profile is the only packaged configuration that switches the startup TLS assertion off, and the chart refuses every route to it, see "Startup TLS assertion") |
| Database migrations | `open-finance-infrastructure/src/main/resources/db/migration` (schema `sc_pay_request_to_pay`) |
| Container image | `docker build -t payment-request-to-pay-service .` |
| Kubernetes | `deploy/helm/payment-request-to-pay-service` (namespace `payments`) |
| AWS infrastructure | `deploy/terraform` |
| Extraction runbook | [RUNBOOK-EXTRACT-pay-request-to-pay](docs/migration/RUNBOOK-EXTRACT-pay-request-to-pay.md) |
| Regression mapping (monolith endpoints -> this service) | [REGRESSION_MAPPING](docs/migration/REGRESSION_MAPPING.md) |
| Deployment and Well-Architected mapping | [DEPLOYMENT_AND_WELL_ARCHITECTED](docs/architecture/DEPLOYMENT_AND_WELL_ARCHITECTED.md) |
| Event contract | [api/asyncapi/svc-pay-request-to-pay.yaml](api/asyncapi/svc-pay-request-to-pay.yaml) |
| HTTP contract | [api/openapi/request-to-pay-service.yaml](api/openapi/request-to-pay-service.yaml) (realignment: Proposed [ADR](docs/architecture/decisions/ADR-local-rtp-openapi-realignment.md)) |

TPP admission (fail closed): besides `aud` and the `payments` scope, a token must carry the claim
`fbx_client_type` = `open-finance-tpp` (`requesttopay.security.tpp.client-type-claim` and `.client-type`).
A token without the claim, or with another value, is 403. The realm's default client scope
`fbx-client-type-open-finance-tpp` emits it on every open-finance TPP client (identity realm-as-code,
Proposed). Service clients (`svc-*`) and the first-party channels are refused even with the claim. Which
TPPs reach the service during the cut-over is decided at the gateway (cohort `rtp-cutover-cohort`), not here.

### Startup TLS assertion

The service stops at startup (`TlsEnforcement`, before any bean is created) unless every datasource URL a pool
can use (`spring.datasource.url`, and `spring.datasource.hikari.jdbc-url` and `spring.flyway.url` when set)
carries `sslmode=verify-full` and, when a Kafka client is configured, the producer's effective `security.protocol`
(after `spring.kafka.properties` and `spring.kafka.producer.properties`) is `SASL_SSL` (`SSL` is accepted for the
`kafka-strimzi` mutual-TLS profile). Each URL is read the way PgJDBC reads it: parameter names are case-sensitive
(`SSLMODE` is ignored by the driver, so it counts as no sslmode) and a repeated parameter keeps its last value, so
a repeated `sslmode` or `sslrootcert` is refused, as are `sslfactory`, `sslfactoryarg`, `sslhostnameverifier`,
`sslpasswordcallback` and `service`. The migration Job (`migrate`) imports the same check. `fintechbankx.tls.enforce`
is `true` in `application.yml`; only the `local` profile (regression's local parity runs) and the bootstrap test
resources set it `false`, and `TlsEnforcementTest` asserts that no other packaged `application-*.yml` does.

The chart refuses every route to that switch at render time (`deploy/helm`, `deployability.yml`):

- `templates/_fbx_helpers.tpl` is the platform chart's helper file, vendored unchanged (its README, "Vendoring
  the guard", is the contract): repo `fintechbankx-platform-delivery-iac-cicd-templates`, commit `6b6c317`, path
  `charts/fintechbankx-service/templates/_helpers.tpl`, sha256
  `8ba2e4a11ead019c25bf0a01e4fe4bbabb6fef0e1980e5fa828ccb9f5673f8ba`; the `deploy/helm` job runs
  `scripts/ci/verify-vendored-guard.sh` (a byte copy of the platform script at the same commit) with that digest:
  one header-less guard file, no other `fbx.*` definition, and `fbx.guard` first in the Deployment and the
  migration Job. `rtp.guard` (`templates/_helpers.tpl`) calls `fbx.guard` once at the top of the Deployment and of the
  migration Job with an adapter dict that maps every route this chart renders: `config`, `extraEnv` / `envFrom` /
  `extraEnvFrom` (none rendered; refused when set), `databaseCa` (always enabled; `mountPath`, `key` and `configMapName` are the values the templates mount, and the
  guard pins them to `/etc/fintechbankx/rds-ca`, `global-bundle.pem` and `rds-ca-bundle`), `kafka.runtime` from
  `kafka.profile` (`kafka-msk` -> `msk`, `kafka-strimzi` -> `strimzi`, anything else fails) and the fixed
  ExternalSecret keys (`SPRING_DATASOURCE_PASSWORD`; `DB_MIGRATION_USERNAME` / `_PASSWORD` as `extraData`;
  `dataFrom` refused). `fbx.guard` parses `config.DB_URL` as PgJDBC does (exactly one `sslmode=verify-full`,
  exactly one `sslrootcert` equal to the mounted bundle, lower-case TLS keys, no bypass parameter, no
  percent-encoding, no TLS key before the `?`, no `${...}` or `$(...)`), refuses the override names in any
  relaxed-binding spelling (`spring.datasource|flyway|liquibase|r2dbc.*`, `spring.application.json`,
  `*jdbc_url*`, `*ssl_factory*`, `*ssl_hostname_verifier*`, `*ssl_password_callback*`, `*ssl_root_cert*`,
  `*ssl_mode*`, `spring.config.*`, `spring.profiles.*`, `spring.ssl.*`, `*ssl_bundle*`, `fintechbankx.tls.*`,
  `DB_URL` in another spelling), requires `*SECURITY_PROTOCOL` values to match the runtime (`SASL_SSL` for
  `kafka-msk`, `SSL` for `kafka-strimzi`) and endpoint identification to stay `https`, checks
  `JAVA_TOOL_OPTIONS` / `JDK_JAVA_OPTIONS` / `_JAVA_OPTIONS` values, and refuses keys that are not ConfigMap or
  Secret keys. `SPRING_PROFILES_ACTIVE` is rendered through `fbx.kafkaProfile`.
- `templates/_helpers.tpl` keeps only what `fbx.guard` does not do (`rtp.guardConfigName`): every
  `spring.kafka.properties.*` name is refused (the guard refuses its `ssl.*`, `security.protocol` and endpoint
  identification names and accepts the rest, such as `sasl.*`; this chart takes them all from the profile), and
  `extraEnv` fails the render because this chart renders none. The JVM option rule (no `fintechbankx` or `kafka`)
  is `fbx.validateJvmOptions`' since `6b6c317`.
- `deployability.yml` carries the platform guard's tests (database CA mount, Kafka client TLS, DocumentDB,
  property-reading and relaxed-binding names, `databaseCa.configMapName` null) as `refuse '<message>'` render
  probes, each with the guard's real message; cases that need a value this chart lacks (`javaToolOptions`,
  ExternalSecret `data` / `extraData`, `databaseCa.enabled`) are not copied.
- Every key and value the templates interpolate into the ConfigMap, the ExternalSecrets and the pod specs is
  quoted (`{{ $key | quote }}: {{ $value | quote }}`, `secretKey`, `property`, `remoteRef.key`, env values,
  Secret and ConfigMap names, labels), numbers go through `int`, and `deploy/helm` proves that a config key or
  value containing a newline cannot add a key the guard never saw.

### AsyncAPI gate

`ci/test` validates `api/asyncapi/*.yaml` with `@asyncapi/cli@2.13.0` and runs the catalog's breaking-change
check against `origin/main` (`ASYNCAPI_DIR=api/asyncapi`, ADR-019 section 5). The check's scripts and the
shared envelope are copies of the asyncapi catalog (`fintechbankx-governance-api-contracts-asyncapi-catalog`)
at commit `44837cc`, unchanged; the step "AsyncAPI gate scripts match the catalog copy" fails when a copy's
sha256 differs:

| Copy | sha256 |
|---|---|
| `scripts/ci/asyncapi-breaking.mjs` | `de255737fe6b54ffbadce8e030e18eec48d0137e0abd43c790fe201a10015eb1` |
| `scripts/ci/asyncapi-breaking.sh` | `5b39d588673c5f7ab55fcfa548fffd70b96ab9b11ea82bd2b61468dadb430c77` |
| `scripts/ci/lib/asyncapi-model.mjs` | `212df6ca092e1ed5519664d7848c9de5fdb5d137f34faa3d31a25e3fe29b3847` |

`api/asyncapi/common/event-envelope.yaml` is the catalog's `asyncapi/common/event-envelope.yaml` at the same
commit. Waivers go in `api/asyncapi/<spec-name>.accepted-breaking.txt` and need the API owner's review
(CODEOWNERS); the spec is not on `origin/main` yet, so the gate skips it as a new file and none is needed.

Events: one topic per aggregate (ADR-019, owner decision 2026-10-08). Every PayRequest event goes to
`evt.pay.rtp.v1`, keyed by the aggregateId, with the UTF-8 headers `eventType`, `eventId`, `correlationId`,
`x-fapi-interaction-id` (every flow starts at the FAPI API) and `traceparent` when traced. History: until
2026-10-08 the contract used one topic per event type; nothing was ever published to those topics, and
migration V7 points outbox rows written before it at `evt.pay.rtp.v1`.

Module layout (layout B): `open-finance-domain` (PayRequest aggregate, events, ports) ← `open-finance-application`
(use cases) ← `open-finance-infrastructure` (JPA, JDBC idempotency, outbox, REST, security) ← `open-finance-bootstrap`
(Spring Boot application).

## Ownership metadata

| Key | Value |
|---|---|
| bounded_context | payment_request_to_pay (payments) |
| owning_squad | Recurring and Bulk Payments Squad |
| data_owner | `db_pay_request_to_pay_<env>`, schema `sc_pay_request_to_pay` (`pay_request`, `pay_request_idempotency`, `dpop_proof_jti`, `outbox_event`) |
| upstream_dependencies | Keycloak realm `fintechbankx` (tokens, `aud` must contain `svc-pay-request-to-pay`); no synchronous calls to other services |
| published_events | `evt.pay.rtp.v1`, one topic for the PayRequest aggregate (ADR-019): `Payments.PayRequest.Created.v1`, `Payments.PayRequest.Accepted.v1`, `Payments.PayRequest.Rejected.v1`, named by the `eventType` header, key = aggregateId, through the transactional outbox |
| consumed_events | none |
| mesh callers needing an ALLOW rule | `cluster.local/ns/istio-ingress/sa/istio-ingressgateway` (TPP API traffic) |

## Removed from the extraction seed

The seed carried generic open-finance code that is not request-to-pay. It was never compiled here (the module source
sets only included `requesttopay/**`) and is removed:

| Removed | Owner |
|---|---|
| Consent aggregate, consent events, `ConsentController`, consent sagas, `DistributedConsentService` (Spring in the domain), Redis consent cache, consent CQRS projection | `fintechbankx-openfinance-consent-auth-service` |
| `OpenFinanceAccountController`, `OpenFinanceLoanController`, data-sharing saga | account-information and lending open-finance services |
| Participant model, CBUAE directory adapter, FAPI authenticator, PCI guard | open-finance platform / identity |
| Mongo consent analytics, compliance monitoring and reporting, PostgreSQL event store | compliance and analytics contexts |
| `infra/terraform/request-to-pay-service` (referenced a modules directory that does not exist here) | replaced by `deploy/terraform` |
| Redis idempotency and DPoP jti stores, direct Kafka publisher to `rtp.pay_requests.v1` | replaced by PostgreSQL stores and the outbox in this repository |

## Dokümantasyon ve Referanslar
- [Enterprise Architecture Hub](https://github.com/COPUR/fintechbankx-governance-architecture-enablement-enterprise-architecture)
- [Secure Microservices Architecture](https://github.com/COPUR/fintechbankx-governance-architecture-enablement-enterprise-architecture/blob/main/docs/architecture/overview/SECURE_MICROSERVICES_ARCHITECTURE.md)
- [Service Data Ownership Matrix](https://github.com/COPUR/fintechbankx-governance-architecture-enablement-enterprise-architecture/blob/main/docs/enterprisearchitecture/implementation-development/SERVICE_DATA_OWNERSHIP_MATRIX.md)
- [Service API Contracts Index](https://github.com/COPUR/fintechbankx-governance-architecture-enablement-enterprise-architecture/blob/main/docs/enterprisearchitecture/implementation-development/SERVICE_API_CONTRACTS_INDEX.md)
- [Transformation Plan](https://github.com/COPUR/fintechbankx-governance-architecture-enablement-enterprise-architecture/blob/main/docs/enterprisearchitecture/implementation-development/MICROSERVICES_TRANSFORMATION_PLAN.md)
- [Capability Map (PUML)](https://github.com/COPUR/fintechbankx-governance-architecture-enablement-enterprise-architecture/blob/main/docs/puml/service-mesh/enterprise-capability-map.puml)
- [Bu Repo Dokümantasyonu](./docs)

## Güvenlik ve Uyumluluk Notları
- Gerçek secret değerleri repo veya `.env` içinde tutulmaz.
- Secret üretim/rotasyon olayları merkezi log/SIEM'e taşınır.
- CI pipeline, anonimlik ve local-path sızıntısı kontrollerini bloklayıcı olarak çalıştırır.

## Katkı
- Katkı süreci için `CONTRIBUTING.md` ve squad runbook'ları izlenmelidir.
- PR'larda mimari kararlar ADR veya backlog referansı ile ilişkilendirilmelidir.

<!-- cell-architecture-start -->
## Cell-Based Architecture

This repository participates in the FinTechBankX cell-based resilience program.

- Plan: docs/architecture/CELL_BASED_ARCHITECTURE_IMPLEMENTATION_PLAN.md
- Backlog: docs/project-management/CELL_ARCHITECTURE_BACKLOG_BOARD.md
<!-- cell-architecture-end -->
