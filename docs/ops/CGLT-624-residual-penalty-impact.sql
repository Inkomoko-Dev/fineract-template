-- CGLT-624 — Residual penalty charge after waiver: impact assessment & correction support
--
-- Context
-- -------
-- A penalty charge could be waived, then a subsequent ACCRUAL transaction re-posted penalty
-- against the (already waived) charge, leaving amount_outstanding_derived > 0 while waived = 1.
-- The charge then read as "waived" but still had an outstanding balance that the old code
-- refused to waive again (ALREADY_WAIVED). The permanent fix ships in backend commit 289cd142d
-- (accrual nets out waived amount; residual penalty re-waive allowed) and UI branch CGLT-624.
--
-- This file contains READ-ONLY assessment queries. Run them in each environment (production
-- included) to produce the authoritative correction worklist and the before-state audit snapshot.
-- Do NOT bulk-mutate from here — active loans are corrected operationally via the fixed "Waive
-- Loan Charges" UI (which auto-audits a Note + before/after change set); only the small number of
-- non-active (closed/written-off/overpaid) loans that the API cannot re-waive need a DBA-run,
-- snapshotted SQL correction with sign-off.

-- 1) Headline: how many charges / loans are currently stuck, and total residual.
SELECT COUNT(*)                                    AS stuck_charges,
       COUNT(DISTINCT lc.loan_id)                  AS affected_loans,
       ROUND(SUM(lc.amount_outstanding_derived), 2) AS total_residual
FROM m_loan_charge lc
WHERE lc.is_penalty = 1
  AND lc.waived = 1
  AND lc.amount_outstanding_derived > 0;

-- 2) Breakdown by loan status. Active (300) loans are re-waivable via the fixed UI; non-active
--    loans (600 closed, 601 written-off, 602/700 overpaid/other) are blocked by the backend
--    LOAN_INACTIVE guard and require a snapshotted DBA correction (see step 4).
SELECT l.loan_status_id                             AS status_id,
       COUNT(*)                                     AS stuck_charges,
       COUNT(DISTINCT l.id)                         AS loans,
       ROUND(SUM(lc.amount_outstanding_derived), 2) AS residual
FROM m_loan_charge lc
JOIN m_loan l ON l.id = lc.loan_id
WHERE lc.is_penalty = 1
  AND lc.waived = 1
  AND lc.amount_outstanding_derived > 0
GROUP BY l.loan_status_id
ORDER BY stuck_charges DESC;

-- 3) Full worklist / before-state snapshot. Export this result set and retain it for audit and
--    rollback. account_no identifies the loan for operators; charge_id for DBA correction.
SELECT lc.loan_id,
       l.account_no,
       l.loan_status_id,
       lc.id                          AS charge_id,
       lc.amount,
       lc.amount_paid_derived         AS amount_paid,
       lc.amount_waived_derived       AS amount_waived,
       lc.amount_outstanding_derived  AS amount_outstanding
FROM m_loan_charge lc
JOIN m_loan l ON l.id = lc.loan_id
WHERE lc.is_penalty = 1
  AND lc.waived = 1
  AND lc.amount_outstanding_derived > 0
ORDER BY l.loan_status_id, lc.amount_outstanding_derived DESC;

-- 4) OPTIONAL correction template for NON-ACTIVE loans only (loan_status_id <> 300), to be run by
--    a DBA under change control AFTER capturing the step-3 snapshot and obtaining Finance/Ops
--    sign-off. Active loans should be corrected through the UI instead. Review the selected rows
--    before running; wrap in an explicit transaction so it can be rolled back.
--
--    Rollback: the step-3 export is the before-state. To restore a charge, reverse the deltas
--    (subtract the moved residual from amount_waived_derived and restore amount_outstanding_derived)
--    and re-run the loan-summary recompute (step 4c).
--
-- 4a) Move the residual from outstanding into waived on the affected charges.
-- START TRANSACTION;
-- UPDATE m_loan_charge lc
-- JOIN m_loan l ON l.id = lc.loan_id
-- SET lc.amount_waived_derived      = lc.amount_waived_derived + lc.amount_outstanding_derived,
--     lc.amount_outstanding_derived = 0
-- WHERE lc.is_penalty = 1
--   AND lc.waived = 1
--   AND lc.amount_outstanding_derived > 0
--   AND l.loan_status_id <> 300;
--
-- 4b) Verify no non-active residual remains before committing.
-- SELECT COUNT(*) AS remaining_non_active_residual
-- FROM m_loan_charge lc
-- JOIN m_loan l ON l.id = lc.loan_id
-- WHERE lc.is_penalty = 1 AND lc.waived = 1 AND lc.amount_outstanding_derived > 0
--   AND l.loan_status_id <> 300;
--
-- 4c) Recompute the parent loans' penalty summary columns from their charges, then commit.
-- UPDATE m_loan l
-- JOIN (
--     SELECT loan_id,
--            SUM(amount_waived_derived)       AS waived,
--            SUM(amount_outstanding_derived)  AS outstanding
--     FROM m_loan_charge
--     WHERE is_penalty = 1
--     GROUP BY loan_id
-- ) agg ON agg.loan_id = l.id
-- SET l.penalty_charges_waived_derived      = agg.waived,
--     l.penalty_charges_outstanding_derived = agg.outstanding
-- WHERE l.loan_status_id <> 300;
-- COMMIT;
