# CGLT-679 Bulk Interest Rate Change — Operator Runbook

## Surface

Bulk interest-rate / reschedule is a **Community App maker-checker UI** backed by CBS REST `/bulk-reschedule`. It is **not** a backend CLI tool.

## Performance / acceptance

- Execute worker processes loans in pages of **`BATCH_SIZE = 100`** (`BulkRescheduleExecutionService`).
- Worker **lease renew** runs each batch; if the lease is lost, stop and recover/resume rather than double-running.
- Operator acceptance for ~1,000 Active loans (e.g. South Sudan CRF-0004): run completes without worker lease loss or hard timeout; incomplete work resumes from `PREVIEW_MATCHED` rows.
- This is an operational acceptance check, not a measured latency SLA.

## Crash recovery / rollback

- Successful execute rows store `rescheduleRequestId` early (before note/snapshot).
- Created reschedule requests are stamped with `bulkExecutionId=<executionId>` in the reason comment for attribution.
- Rollback persists `ROLLBACK_REASON: …` on `BulkRescheduleResult.errorMessage` (status `ROLLED_BACK`) and a loan note that includes `resultId=…`.
