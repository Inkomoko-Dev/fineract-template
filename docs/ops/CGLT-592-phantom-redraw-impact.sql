-- CGLT-592 — Loans wrongly OVERPAID / "closed with arrears" after payments + deposit redraw:
--             impact assessment & correction support
--
-- Context
-- -------
-- After a repayment, the write path unconditionally swept loan.getTotalOverpaid() into the
-- (fork-local) redraw account and minted a DEPOSIT_REDRAW (txn type 27):
--
--     if (loan.getTotalOverpaid() != null) {                          // NO settlement check
--         loanRepositoryWrapper.updateRedrawAmount(..., isDeposit=true);
--     }
--     -- LoanWritePlatformServiceJpaRepositoryImpl (repayment path)
--
-- getTotalOverpaid() can be a *transient / false* surplus while a balance is still outstanding
-- (flat-interest advance allocation, mid-reprocess states). Two compounding effects followed:
--   1) A phantom DEPOSIT_REDRAW was minted even though the loan owed money, and the redraw
--      account balance was INCREMENTED on every repayment/reprocess — so a stuck loan balloons
--      its redraw balance without bound (see the two multi-billion/trillion outliers below).
--   2) Loan.getTotalPaidInRepayments() counted the DEPOSIT_REDRAW as "paid", re-manufacturing the
--      same overpayment on every recompute, pinning the loan to status 700 (OVERPAID) even with a
--      large real outstanding balance -> presents to the field as "closed with arrears".
--
-- Permanent fix (branch CGLT-592):
--   * Gate the sweep: only deposit into the redraw account when the loan is fully settled
--     (getSummary().getTotalOutstanding() == 0 && overpaid > 0)  -- stops NEW phantom redraws.
--   * Merged CGLT-645 Loan.java: deposit redraw now CONSUMES an overpayment (subtracted in
--     calculateTotalOverpayment) instead of re-manufacturing it, and no longer short-circuits the
--     lifecycle checks -- so a reprocess of an already-corrupt loan drops the phantom overpayment.
--
-- This file is READ-ONLY assessment plus a DBA/Finance correction TEMPLATE (kept inside a
-- ROLLBACK-by-default transaction). Do NOT bulk-mutate blindly. Correct loan status/overpaid via a
-- platform reprocess on the fixed build; the redraw-account ledger is NOT recomputed by reprocess
-- and needs the snapshotted SQL cleanup in section 4, with Finance sign-off.
-- Example loan from the ticket: 000407390 (id 407390).

-- =====================================================================================
-- 1) HEADLINE BLAST RADIUS
-- =====================================================================================

-- 1a) Redraw ledger exposure: how many loans carry a redraw balance and the phantom total.
SELECT COUNT(*)                         AS loans_with_redraw_balance,
       ROUND(SUM(redraw_balance), 2)    AS total_redraw_balance
FROM m_loan_redraw_account
WHERE redraw_balance <> 0;

-- 1b) Loans flagged OVERPAID(700) that still have a real outstanding balance -- the most visibly
--     wrong ("closed with arrears") accounts. 407390 is one of these.
SELECT id, account_no, total_outstanding_derived, total_overpaid_derived
FROM m_loan
WHERE loan_status_id = 700
  AND total_outstanding_derived > 0
ORDER BY total_outstanding_derived DESC;

-- 1c) Full phantom-overpaid population: OVERPAID(700) loans carrying a live DEPOSIT_REDRAW.
SELECT COUNT(*) AS overpaid_loans_with_deposit_redraw
FROM m_loan l
WHERE l.loan_status_id = 700
  AND EXISTS (SELECT 1 FROM m_loan_transaction t
              WHERE t.loan_id = l.id AND t.transaction_type_enum = 27 AND t.is_reversed = 0);

-- 1d) Catastrophic outliers -- runaway compounding of the sweep. Review individually.
SELECT loan_id, redraw_balance
FROM m_loan_redraw_account
WHERE redraw_balance > 100000000
ORDER BY redraw_balance DESC;

-- =====================================================================================
-- 2) THE TICKET'S 15 LOANS (authoritative before-state snapshot)
-- =====================================================================================
SELECT l.id,
       l.loan_status_id                                             AS status_id,
       l.total_outstanding_derived                                  AS outstanding,
       l.total_overpaid_derived                                     AS overpaid,
       r.redraw_balance,
       (SELECT COUNT(*) FROM m_loan_transaction t
        WHERE t.loan_id = l.id AND t.transaction_type_enum = 27 AND t.is_reversed = 0) AS live_deposit_redraws,
       (SELECT COALESCE(SUM(t.amount), 0) FROM m_loan_transaction t
        WHERE t.loan_id = l.id AND t.transaction_type_enum = 27 AND t.is_reversed = 0) AS deposit_redraw_amt
FROM m_loan l
LEFT JOIN m_loan_redraw_account r ON r.loan_id = l.id
WHERE l.id IN (417199,425068,426085,416883,420457,405946,413857,416452,
               406548,425784,417974,407390,421442,421454,417276)
ORDER BY l.id;

-- 2b) Per-transaction proof that a redraw was phantom: it was minted while a balance was still
--     outstanding (outstanding_loan_balance_derived at/after the redraw is > 0).
--     Parameterise :loanId (e.g. 407390).
SELECT id, transaction_type_enum, transaction_date, amount,
       outstanding_loan_balance_derived AS bal_after, is_reversed, created_on_utc
FROM m_loan_transaction
WHERE loan_id = 407390
  AND transaction_type_enum IN (2, 27)           -- REPAYMENT, DEPOSIT_REDRAW
ORDER BY transaction_date, id;

-- =====================================================================================
-- 3) CORRECTION — loan status & overpaid  (PREFERRED: platform reprocess, no raw SQL)
-- =====================================================================================
-- Step 0. Deploy CGLT-592 first. This stops NEW phantom redraws AND makes reprocess subtract the
--         existing DEPOSIT_REDRAW from the overpayment (clamped at 0), so the loan returns to
--         ACTIVE(300)/Closed-Obligations-Met(600) with overpaid = NULL.
-- Step 1. Reprocess each affected loan through the platform (recalculate/reprocess transactions),
--         NOT raw UPDATEs of m_loan_*_derived -- reprocess reverses corrupt txns and re-posts GL.
--         Start with the 4 status-700-with-outstanding loans (section 1b), then the wider
--         population from 1c. Validate a handful before bulk-running.
-- Step 2. Re-run sections 1b/1c/2 -- expect 1b -> 0 rows, and the ticket loans -> status 300/600
--         with overpaid NULL and unchanged real outstanding.

-- =====================================================================================
-- 4) CORRECTION — redraw account ledger  (DBA SQL, FINANCE SIGN-OFF REQUIRED)
-- =====================================================================================
-- Reprocess does NOT recompute m_loan_redraw_account. After section 3, a loan's legitimate redraw
-- balance is at most its genuine remaining overpayment; a phantom-only loan should be 0. Target:
--
--     target_redraw_balance = GREATEST(COALESCE(reprocessed total_overpaid_derived, 0), 0)
--
-- Verify the target set BEFORE mutating:
SELECT r.loan_id, r.redraw_balance AS current_balance,
       COALESCE(l.total_overpaid_derived, 0) AS target_balance,
       (r.redraw_balance - COALESCE(l.total_overpaid_derived, 0)) AS phantom_reduction
FROM m_loan_redraw_account r
JOIN m_loan l ON l.id = r.loan_id
WHERE r.redraw_balance <> COALESCE(l.total_overpaid_derived, 0)
ORDER BY phantom_reduction DESC;

-- Correction template — ROLLBACK by default. Snapshot m_loan_redraw_account to a backup table
-- first; flip to COMMIT only after Finance sign-off and a fresh diff of the query above.
-- The two catastrophic outliers (section 1d) MUST be reviewed and corrected individually, not by
-- this blanket UPDATE.
START TRANSACTION;

--   CREATE TABLE bak_m_loan_redraw_account_cglt592 AS SELECT * FROM m_loan_redraw_account;

UPDATE m_loan_redraw_account r
JOIN m_loan l ON l.id = r.loan_id
SET r.redraw_balance      = GREATEST(COALESCE(l.total_overpaid_derived, 0), 0),
    r.lastmodified_date   = UTC_TIMESTAMP()
WHERE r.redraw_balance <> COALESCE(l.total_overpaid_derived, 0)
  AND r.loan_id NOT IN (402667, 423413);          -- outliers reviewed separately

-- Post-check (still inside the transaction):
SELECT COUNT(*) AS rows_still_mismatched
FROM m_loan_redraw_account r
JOIN m_loan l ON l.id = r.loan_id
WHERE r.redraw_balance <> COALESCE(l.total_overpaid_derived, 0)
  AND r.loan_id NOT IN (402667, 423413);

ROLLBACK;   -- change to COMMIT only after sign-off
-- =====================================================================================
-- 5) VALIDATION (post-correction, all should return 0 rows / expected)
-- =====================================================================================
-- 5a) No loan is OVERPAID while owing money.
SELECT COUNT(*) FROM m_loan WHERE loan_status_id = 700 AND total_outstanding_derived > 0;
-- 5b) No redraw balance exceeds the loan's genuine overpayment.
SELECT COUNT(*) FROM m_loan_redraw_account r JOIN m_loan l ON l.id = r.loan_id
WHERE r.redraw_balance > COALESCE(l.total_overpaid_derived, 0) + 0.005;
-- 5c) Ticket loans land ACTIVE(300)/Closed(600) with overpaid NULL and unchanged real outstanding.
SELECT id, loan_status_id, total_outstanding_derived, total_overpaid_derived
FROM m_loan WHERE id IN (407390, 406548, 417974) ORDER BY id;
