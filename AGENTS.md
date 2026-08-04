# AGENTS.md

## Architecture Overview

This codebase implements a **Request to Pay (RtP)** service within the FinTechBankX Open Finance ecosystem, following **Hexagonal Architecture** (ports & adapters), **Domain-Driven Design (DDD)**, and **Event-Driven Architecture**. It's structured as a bounded context (`payment_request_to_pay`) with strong isolation boundaries.

### Key Components
- **Domain Layer** (`open-finance-domain`): Pure Java business logic, no framework dependencies. Contains aggregates (e.g., `PayRequest`), value objects, events, and ports.
- **Application Layer** (`open-finance-application`): Orchestrates domain use cases and complex sagas (e.g., `DataSharingRequestSaga`) using Spring Context/Transactions and a `SagaOrchestrator`.
- **Infrastructure Layer** (`open-finance-infrastructure`): Adapters for persistence (PostgreSQL + Redis), messaging (Kafka), web (Spring Web), and security (OAuth2/DPoP).
- **API Contracts**: OpenAPI specs in `api/openapi/` with FAPI 2.0 compliance (DPoP, mTLS, interaction IDs).
- **Cell-Based Resilience**: Participates in enterprise cell architecture for blast radius reduction (see `docs/architecture/CELL_BASED_ARCHITECTURE_IMPLEMENTATION_PLAN.md`).

### Data Flow

#### Request to Pay (RtP)
1. TPP creates RtP via `POST /par` (idempotent, DPoP-secured).
2. Domain validates, persists to PostgreSQL, emits `PayRequestCreatedEvent` to Kafka.
3. Notification service pushes to debtor's app.
4. Debtor accepts/rejects via `POST /par/{id}/accept` or `/reject`, triggering payment orchestration saga.

#### Data Sharing Request (Saga)
1. Consent validated for data access.
2. Rate limits checked and quotas registered.
3. Data aggregated across platforms (**Loan Management**, **AmanahFi**, **Masrufi**).
4. Data transformed and encrypted for the requesting participant.
5. Secure delivery with full audit trail and compliance recording.

### External Dependencies
- **PostgreSQL**: System of record for RtP state and audit.
- **Redis**: Idempotency cache to prevent duplicate requests.
- **Kafka**: Event streaming for notifications and cross-service communication.
- **Istio Service Mesh**: mTLS, circuit breakers, deny-by-default policies.

## Developer Workflows

### Build & Test
- **Build**: `./gradlew assemble` (excludes tests).
- **Test**: `./gradlew check` (runs unit/integration tests + JaCoCo coverage verification >=85%).
- **Full Cycle**: `make all` (clean, build, test, security via Gitleaks).
- **Coverage Report**: `./gradlew jacocoTestReport` (HTML/XML in `build/reports/jacoco/`).

### Debugging
- **Local Run**: Use Spring Boot dev tools; debug via IDE (IntelliJ IDEA recommended).
- **Logs**: Structured JSON via Logstash encoder; trace IDs via Micrometer/Zipkin.
- **Security Checks**: `scripts/ci/fapi-dpop-guard.sh` validates OpenAPI for FAPI/DPoP headers.
- **API Diff**: `scripts/ci/oasdiff-breaking.sh` detects breaking changes in specs.

### CI/CD
- **Branching**: `main` (prod), `dev` (integration), `staging` (pre-prod), `local` (dev). Features: `codex/<short-desc>`.
- **Gates**: JaCoCo >=85%, ArchUnit for domain purity, Gitleaks for secrets, OAS diff for contracts.
- **Deployment**: Terraform in `infra/terraform/` for AWS/K8s.

## Project Conventions

### Code Patterns
- **Immutability**: Use Java Records for domain models/events (e.g., `PayRequest`, `PayRequestCreatedEvent`).
- **Validation**: Compact constructors in records for business rules (e.g., positive amounts, non-blank fields).
- **Business Logic**: Methods on aggregates (e.g., `PayRequest.reject()`, `consume()`) with state transitions.
- **Ports & Adapters**: In ports for use cases, out ports for external services (e.g., `PayRequestRepositoryPort`).
- **Events**: Domain events as records with UUIDs, timestamps, versions.
- **Security**: All endpoints require `Authorization: DPoP <token>`, `DPoP`, `X-FAPI-Interaction-ID`, `X-Idempotency-Key` (write ops).
- **Error Handling**: Domain exceptions for business rules; HTTP 400 for idempotency violations.

### Examples
- **Aggregate State Machine**: `PayRequestStatus` enum with `AWAITING_AUTHORISATION` → `REJECTED` | `CONSUMED`.
- **Saga Orchestration**: `DataSharingRequestSaga` handles multi-step data flows with automated compensations (e.g., `restoreQuota`, `cleanupAggregatedData`).
- **Idempotency**: Redis-backed shield prevents duplicate `consentId` creations.
- **Event Payload**: JSON with `eventId`, `aggregateId`, `occurredOn` (see `IMPLEMENTATION_PLAN.md` samples).
- **API Response**: `201 Created` with `consentId`, `status`, `createdAt` (ISO 8601).

### Testing
- **Unit**: JUnit 5 + Mockito + AssertJ; focus on domain logic.
- **Integration**: Spring Boot tests for adapters; WireMock for external mocks.
- **Coverage**: Enforced >=85% line coverage; reports in `build/reports/jacoco/`.

### Dependencies
- **Domain**: Core Java 23 (no Spring).
- **Application**: `spring-context`, `spring-tx`.
- **Infrastructure**: `spring-boot-starter-web`, `spring-boot-starter-data-jpa` (PostgreSQL), `spring-boot-starter-data-redis`, `spring-kafka`, `spring-boot-starter-oauth2-resource-server`, `nimbus-jose-jwt` (DPoP).

## Key Files
- `IMPLEMENTATION_PLAN.md`: Phased rollout with samples (SQL, JSON, HTTP).
- `docs/architecture/CELL_BASED_ARCHITECTURE_IMPLEMENTATION_PLAN.md`: Resilience boundaries.
- `api/openapi/request-to-pay-service.yaml`: API contract with security schemas.
- `build.gradle` (root): Multi-module setup with Java 23 toolchain.
- `Makefile`: Automation for build/test/security.
- `scripts/ci/`: Custom guards for FAPI, OAS diff, coverage trends.
