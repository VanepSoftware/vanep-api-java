## 0. Preparation

- [ ] 0.1 Confirm the active branch and next available Flyway version after V51; do not edit V17, V21 or V41–V48.
- [ ] 0.2 Resolve the notification delivery channel (existing e-mail, FCM, or persisted event only), optional-document blocking rule, and plan-block reason contract.
- [ ] 0.3 Audit staging data to size the backfill of approved drivers and their valid mandatory documents.
- [ ] 0.4 Review the revised proposal, design, and specs; obtain approval before code generation.

## 1. PR Plan

| Phase | Contents | Depends on | Parallel with |
| :--- | :--- | :--- | :--- |
| 1 | Compliance state migration, policy, backfill and tests | — | — |
| 2 | Document/CNH revalidation and Admin approval integration | Phase 1 | — |
| 3 | Search/profile/client-driver gate and compliance API | Phases 1–2 | — |
| 4 | Daily alert job and selected notification adapter | Phase 1 | Phase 3 after the compliance API is stable |

## 2. Dependency Graph and Layer Assignment

```
[Flyway state + alert ledger + policy]
                    |
        +-----------+------------+
        |                        |
        v                        v
[CNH/document writes + Admin] [Daily alert scheduler]
        |
        v
[Search/profile/client-driver + compliance API]
```

## 3. Phase 1 — Compliance State and Policy Foundation

- [ ] 3.1 Write failing policy and repository tests for missing, pending, rejected, expired and regular states, including the D0/D+1 boundary with a fixed `Clock`.
- [ ] 3.2 Add a new Flyway migration for driver document-compliance fields, CNH last notification metadata, unique alert-event ledger, indexes, and an auditable backfill for approved drivers with valid attached mandatory documents.
- [ ] 3.3 Implement the framework-free `DriverDocumentCompliancePolicy`, result/reason enum, state persistence, and batch reads that avoid N+1 queries.
- [ ] 3.4 Implement the transactional refresh service and invoke it in the migration/backfill path.
- [ ] 3.5 Add tests that assert no media table, URL field, S3 client, or public-file route is introduced by this phase.
- [ ] 3.6 Run `make lint` and `make test-coverage`; keep the phase within the PR size limit.

## 4. Phase 2 — Revalidation and Existing Admin Workflow

- [ ] 4.1 Write failing service/controller tests for CNH data/photo replacement, required-document change, mandatory rejection reason, Admin approval guard, and restoration after reapproval.
- [ ] 4.2 Update CNH, document and file-write services to refresh compliance; replacement sets document status to `PENDING` where applicable and moves previously approved drivers to `UNDER_REVIEW`.
- [ ] 4.3 Require a non-blank reason for `REJECTED` driver documents, clear stale reason on approval, and preserve the reason in owner responses.
- [ ] 4.4 Integrate the policy into `DriverOnboardingService.approve` so the existing Admin endpoints cannot approve invalid required documentation.
- [ ] 4.5 Add i18n messages and test that the #47 pending queue receives revalidation cases without a new approval route.
- [ ] 4.6 Run `make lint` and `make test-coverage`; keep the phase within the PR size limit.

## 5. Phase 3 — Eligibility Gates and Compliance API

- [ ] 5.1 Write failing repository, service and MockMvc tests for geosearch, recommended search, public profile, new relationship, activation, owner compliance, and Admin compliance.
- [ ] 5.2 Filter all existing searchable repository queries by both approved account and `REGULAR` document-compliance state without changing rank order or page totals.
- [ ] 5.3 Guard `ClientDriverService.create` and activation with the same compliance state while leaving existing `ACTIVE` links untouched.
- [ ] 5.4 Expose authorized own/Admin compliance endpoints with document and plan reason categories; return an explicit empty plan category until the plan module supplies data.
- [ ] 5.5 Add regression tests proving renewal plus document approval plus #47 account approval restores eligibility automatically.
- [ ] 5.6 Run `make lint` and `make test-coverage`; keep the phase within the PR size limit.

## 6. Phase 4 — Daily Alerts and Idempotency

- [ ] 6.1 Write failing tests with an injected `Clock` for D-60, D-30, D0, D+1 suspension, late recovery after a missed scan, and repeated/concurrent execution.
- [ ] 6.2 Implement the scheduled scan in the `America/Sao_Paulo` business date; it refreshes compliance before creating alerts for CNH and dated documents.
- [ ] 6.3 Persist a unique event per resource and milestone, update compatible `notified_at` values, and treat duplicate-key races as idempotent no-ops.
- [ ] 6.4 Implement only the notification adapter approved in preparation; all tests use a fake adapter and make no FCM, S3, MinIO, SMTP or other external call.
- [ ] 6.5 Add operational logs/metrics for scanned resources, compliance transitions, emitted alerts, duplicate suppression and delivery failures without exposing document content.
- [ ] 6.6 Verify migration/backfill in staging, job schedule, JaCoCo threshold and Spotless before each phase PR.
