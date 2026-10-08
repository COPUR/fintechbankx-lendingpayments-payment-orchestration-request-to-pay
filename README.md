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
| Run locally | `SPRING_DATASOURCE_PASSWORD=... ./gradlew :open-finance-bootstrap:bootRun` (PostgreSQL on localhost:5432, database `db_pay_request_to_pay_local`) |
| Database migrations | `open-finance-infrastructure/src/main/resources/db/migration` (schema `sc_pay_request_to_pay`) |
| Container image | `docker build -t payment-request-to-pay-service .` |
| Kubernetes | `deploy/helm/payment-request-to-pay-service` (namespace `payments`) |
| AWS infrastructure | `deploy/terraform` |
| Extraction runbook | [RUNBOOK-EXTRACT-pay-request-to-pay](docs/migration/RUNBOOK-EXTRACT-pay-request-to-pay.md) |
| Deployment and Well-Architected mapping | [DEPLOYMENT_AND_WELL_ARCHITECTED](docs/architecture/DEPLOYMENT_AND_WELL_ARCHITECTED.md) |
| Event contract | [api/asyncapi/svc-pay-request-to-pay.yaml](api/asyncapi/svc-pay-request-to-pay.yaml) |
| HTTP contract | [api/openapi/request-to-pay-service.yaml](api/openapi/request-to-pay-service.yaml) |

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
| published_events | `evt.pay.rtp.created.v1` (`Payments.PayRequest.Created.v1`), `evt.pay.rtp.accepted.v1` (`Payments.PayRequest.Accepted.v1`), `evt.pay.rtp.rejected.v1` (`Payments.PayRequest.Rejected.v1`), through the transactional outbox |
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
