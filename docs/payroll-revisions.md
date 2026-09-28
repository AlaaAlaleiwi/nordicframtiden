# Finalized payslips and corrections

Implemented for the shared backend, web admin, Apple admin (iOS/macOS), and Android admin payslip screens.

## Workflow

1. New payslips are drafts, even for past work months. Save and review adjustments, then choose **Finalize saved payslip**. Finalization calculates using persisted inputs and stores the result atomically.
2. Finalized payslips are read-only. Profile, rate, schedule and tax-table changes cannot replace their stored amounts. The normal payslip endpoint returns the latest stored revision.
3. Select **Create correction** on the latest version. Enter a reason and signed SEK changes to regular taxable pay, one-time taxable pay, regular withholding, one-time withholding, and tax-free reimbursements. These are changes, not replacement totals. Review tax changes separately: the correction does not call the live tax engine. Include reimbursement evidence references in the reason.
4. Saving appends the next revision with the authenticated operator, server timestamp, reason, signed changes and complete resulting payslip. The earlier versions remain selectable. Reopen the payslip if another operator has already corrected it.
5. The selected finalized revision supplies the displayed and PDF totals. Finalized PDFs omit live shift allocations because legacy snapshots did not preserve the underlying day rows. The PDF identifies its revision.

The Apple screen opens these controls from **Draft · Finalize** / **Revision … · History** in the toolbar. Web and Android show them in the payslip screen. Old clients can still read payslips, but finalized adjustment writes and live previews return HTTP 409.

## Preservation and concurrency

- Migration `V44__immutable_payslip_revisions.sql` copies each existing snapshot verbatim to revision 1, attributed to `legacy-import`. This is the last surviving saved snapshot; earlier versions already overwritten by the old implementation cannot be reconstructed.
- The original snapshot payload never changes. PostgreSQL triggers reject updates to both originals and revisions. Unique `(snapshot_id, revision)` keys and a transactional employee-row lock serialize saves/finalization/corrections. Retrying finalization returns the existing latest revision. Corrections require `expectedRevision` and a nonempty reason; stale requests fail with 409.
- Corrupt or missing stored history fails closed; it is never replaced by a fresh calculation. Inconsistent legacy totals require review before a correction is allowed. Corrections cannot make total pay, withholding, net pay or a taxable pay category negative.
- Draft adjustment writes check both USER and STAFF snapshots because the existing adjustment table is shared by employee/month. A frozen role cannot be indirectly altered through the other role.
- Signed one-time pay corrections feed later draft annual-income estimates without changing the original adjustment rows. Previously finalized estimates remain unchanged.
- GDPR exports include all revisions. Existing explicit account erasure deletes their parent snapshot and cascades revision deletion; the immutable-update triggers do not block that existing workflow.

## API

All management endpoints use existing ADMIN / PERM_SALARIES authorization and `userId`, `year`, `month`, `role` query parameters (role defaults to USER).

- `GET /api/salaries/payslip/revisions`: ordered revision history; empty means draft.
- `POST /api/salaries/payslip/finalize`: finalize persisted inputs, or return the already-finalized latest revision.
- `POST /api/salaries/payslip/corrections`: append the reviewed correction below. The actor and time are always supplied by the server.

```json
{
  "expectedRevision": 1,
  "reason": "Missing agreed allowance",
  "regularGrossDelta": "100.00",
  "oneTimeGrossDelta": "0",
  "regularTaxDelta": "30.00",
  "oneTimeTaxDelta": "0",
  "taxFreeDelta": "0"
}
```

## Scope and rollout

Deploy the backend with V44 before releasing the new clients. Retain a database backup. No production deployment was performed for this change.

This is a payslip preservation and correction workflow. It does not implement the separate Swedish tax-engine fixes in `SWEDISH_PAYROLL_AUDIT.md`, submit employer declarations, transfer money, or redefine the live schedule/cost reports as finalized payroll reports. The existing annual-income estimate still uses the existing year rules; the payment-year issue remains a separate audit item.

## Verification (2026-09-28)

- `mvn -q test`: passed the backend unit suite.
- Focused Maven `verify` with `PayslipFreezeServiceTest,SalariesControllerSecurityTest,SalaryAdjustmentServiceTest,PayrollServiceTest` and `PayslipRevisionIT`: passed against an isolated PostgreSQL database. The five database tests cover exact legacy migration, immutable updates and GDPR deletion, simultaneous finalizations, simultaneous corrections, and future draft one-time estimates. Service tests also cover signed amounts, validation, frozen inputs, stale requests and corrupt/inconsistent history.
- Web: full `npm test` passed (125 tests before the additional finalized-PDF assertion); targeted modal/PDF tests and `npm run build` passed. Targeted ESLint passed.
- Android: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --console=plain` passed; added Moshi tests verify numeric history deltas, signed amounts, decimal-string requests and server-owned attribution.
- Apple: Xcode macOS and generic iOS Simulator builds passed. `PayslipRevisionTests` passed (2 tests) on macOS; native device interaction was not exercised.

An attempted full backend application context initially failed in the independently edited `DeletionPolicy` component (`No default constructor found`). The payroll integration tests use a focused JPA context and pass; this does not certify that unrelated startup issue is resolved.
