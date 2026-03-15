# Migration Granularity Notes

- Repository: `fintechbankx-payments-request-to-pay-service`
- Source monorepo: `enterprise-loan-management-system`
- Sync date: `2026-03-15`
- Sync branch: `chore/granular-source-sync-20260313`

## Applied Rules

- capability extraction: `requesttopay` from `open-finance-context`
- dir: `infra/terraform/services/request-to-pay-service` -> `infra/terraform/request-to-pay-service`
- file: `docs/architecture/open-finance/capabilities/hld/open-finance-capability-overview.md` -> `docs/hld/open-finance-capability-overview.md`
- file: `docs/architecture/open-finance/capabilities/test-suites/request-to-pay-test-suite.md` -> `docs/test-suites/request-to-pay-test-suite.md`

## Notes

- This is an extraction seed for bounded-context split migration.
- Follow-up refactoring may be needed to remove residual cross-context coupling.
- Build artifacts and local machine files are excluded by policy.

