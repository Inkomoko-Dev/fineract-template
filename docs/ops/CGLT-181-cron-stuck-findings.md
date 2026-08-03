# CGLT-181 — Daily late-fee cron "stuck at an old date / never finishes"

Branch `CGLT-181` (now fast-forwarded onto `inkomoko` HEAD — all merged CGLT-181 work present).
Investigation date: 2026-07-09.

## ✅ ROOT CAUSE (confirmed against the real prod-copy DB) — supersedes the hypotheses below

The nightly job **12 "Apply penalty to overdue loans" was failing instantly every night** with:

```
PreparedStatementCallback; SQL [select distinct ml.id from m_loan_repayment_schedule ls ...
  where DATE_SUB(DATE('2026-07-01'), INTERVAL ? day) > ls.duedate and ml.id > ? ... limit ? ];
Parameter index out of range (4 > number of parameters, which is 3).
```

`retrieveAllLoanIdsWithOverdueInstallments` bound **4** values to a **3-placeholder** query. Every run in
`job_run_history` shows `status=failed`, 0 seconds, through 2026-06-30 → penalties froze (max
`penalty_date` = 2026-07-04) → the "stuck at an old date". It was never a hang; the pagination query
threw before doing any work.

**Fix:** commit `f1bd2b15c` (2026-07-01) rewrote the method to pass exactly 3 params
(`penaltyWaitPeriod, maxLoanIdInList, pageSize`); that fix is now merged into this branch. Validated two
ways: (1) `LoanSchedularServiceOverdueCronTest` (green); (2) replaying old vs fixed SQL against the real
DB — old shape → `ERROR: Incorrect arguments to EXECUTE`, fixed shape → returns loan ids. Executing job 12
on the running fixed server advanced `max(penalty_date)` from 2026-07-04 to 2026-07-09 with no errors.

NB: the earlier "backfill O(days) / perf" theories below were **not** the cause — but they explain why the
recovery run is not instant (per-loan finalize still recalculates schedule + reprocesses + accrues).

## What the cron actually does

`JobName.APPLY_CHARGE_TO_OVERDUE_LOAN_INSTALLMENT`
→ `LoanSchedularServiceImpl.applyChargeForOverdueLoans(Map)`
→ key-set paginates overdue loans via
`LoanReadPlatformServiceImpl.retrieveAllLoanIdsWithOverdueInstallments(...)` (`ml.id > ? order by ml.id asc limit ?`)
→ `ApplyChargeToOverdueLoansPoster.call()` per batch
→ `LoanWritePlatformServiceJpaRepositoryImpl.applyOverdueChargesForLoan(loanId, null)`
→ `LoanDailyLateFeeService.processDailyLateFeesForLoan(loanId, businessDate, null, true)`.

## Ruled OUT by red tests (they PASS)

`LoanSchedularServiceOverdueCronTest` drives the cron loop with a stub read service that mirrors the
production key-set pagination. Result — 3/3 green:

- `cronFinishesAndProcessesEveryOverdueLoanExactlyOnce` (250 loans, 4 threads, page 40) — **terminates,
  each loan handed to a poster exactly once, no duplicates.**
- `cronFinishesWhenPortfolioSmallerThanOnePage` — terminates, no reprocessing.
- `cronTerminatesWhenNoOverdueLoans` — terminates.

**Conclusion: the pagination / queue control-flow is NOT an infinite loop and does NOT re-process loans.**
So the hang is not in the schedular loop itself.

## Remaining suspects (per-loan work, ranked)

### 1. First-run backfill is O(days-overdue) per loan, in one transaction — most likely
`processDailyLateFeesForLoan` walks **one calendar day at a time** from `generationStartDate` to the
business date. On a loan's **first** pass `determineGenerationStartDate` → `determineFirstDailyLateFeeDate`
returns the **earliest installment due date + grace + 1** (`.min()` over *all* installments,
`LoanDailyLateFeeService.java:279`). For a loan overdue for months/years this backfills hundreds of days,
each iteration doing `addCharge` (`saveAndFlush` of a `LoanCharge`, plus a charge-applied txn when upfront
accrual is on) + metadata upsert, then a single `finalizeDailyLateFeeChanges` that (with interest
recalculation enabled) regenerates the schedule, archives it, `reprocessTransactions()`, and
`recalculateAccruals()`. Multiply by a large overdue portfolio → the job runs for hours and *appears* not
to finish; poster logs (`LOG.info("Loan ID {}")`) sit on old penalty dates.

### 2. Fatal-if-too-big → permanent stall (explains "stuck", not just "slow")
Each loan is one `@Transactional` unit. If the backfill for a long-overdue loan exceeds a statement/lock
timeout, deadlocks, or OOMs, the transaction **rolls back** → **no `LoanDailyLateFee` rows committed** →
next cron run `determineGenerationStartDate` again finds no active fees → repeats the *same* full backfill
→ fails again. The loan never advances and the job keeps failing on it. This matches "passed QA (freshly
overdue test loans) but breaks in prod (mature overdue book)".

### 3. Stale business date upstream
`applyOverdueChargesForLoan` uses `DateUtils.getBusinessLocalDate()` as the effective date. If the COB /
business-date-advance job is itself stuck, the penalty cron keeps running against an old date and never
catches up — literally "logs stuck at an old date". Check the tenant business date vs wall-clock first;
it's the cheapest thing to rule out.

## Recommended next steps
1. **Confirm which**: pull prod logs for the job — is it looping on the same loan id (→ #1/#2) or is the
   tenant business date old (→ #3)? Check `m_loan_daily_late_fee` for loans with a stale `max(penalty_date)`
   despite long arrears (→ #2 rollback).
2. If #1/#2: bound the per-loan work — resume/checkpoint per committed day (commit incrementally so a
   partial backfill still advances `generationStartDate`), and/or start the backfill from the first
   *overdue* installment rather than the first installment ever.
3. Add an integration red test: a loan overdue ~12 months, assert the first `processDailyLateFeesForLoan`
   completes in bounded time and is idempotent on a second run (expected to expose #1/#2).

## Housekeeping
- Merge of `inkomoko` into `CGLT-181` is clean; `:fineract-provider:compileJava` succeeds.
- `LoanDailyLateFeeServiceTest` (rate calc) passes.
