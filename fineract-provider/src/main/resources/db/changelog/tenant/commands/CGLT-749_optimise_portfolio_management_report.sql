--
-- Licensed to the Apache Software Foundation (ASF) under one
-- or more contributor license agreements. See the NOTICE file
-- distributed with this work for additional information
-- regarding copyright ownership. The ASF licenses this file
-- to you under the Apache License, Version 2.0 (the
-- "License"); you may not use this file except in compliance
-- with the License. You may obtain a copy of the License at
--
-- http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing,
-- software distributed under the License is distributed on an
-- "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
-- KIND, either express or implied. See the License for the
-- specific language governing permissions and limitations
-- under the License.
--

UPDATE stretchy_report
SET report_sql = "WITH scope AS (
    SELECT ounder.id AS office_id
    FROM m_office o
    JOIN m_office ounder ON ounder.hierarchy LIKE CONCAT(o.hierarchy, '%')
        AND ounder.hierarchy LIKE CONCAT('${currentUserHierarchy}', '%')
    WHERE o.id = ${officeId}
),
base_all AS (
    SELECT l.id, l.account_no, l.approvedon_date, l.approved_principal, l.client_id,
        l.closedon_date, l.created_on_utc, l.department_cv_id, l.description,
        l.disbursedon_date, l.expected_maturedon_date, l.fee_charges_outstanding_derived,
        l.fee_charges_repaid_derived, l.fee_charges_waived_derived, l.fund_id, l.group_id,
        l.interest_outstanding_derived, l.interest_repaid_derived, l.interest_waived_derived,
        l.kiva_id, l.last_modified_on_utc, l.loan_decision_state, l.loan_officer_id,
        l.loanpurpose_cv_id, l.loan_status_id, l.loan_type_enum, l.maturedon_date,
        l.penalty_charges_outstanding_derived, l.penalty_charges_repaid_derived,
        l.penalty_charges_waived_derived, l.principal_amount_proposed,
        l.principal_disbursed_derived, l.principal_outstanding_derived,
        l.principal_repaid_derived, l.product_id, l.rejectedon_date,
        l.repayment_period_frequency_enum, l.rescheduledon_date, l.submittedon_date,
        l.term_frequency, l.total_overpaid_derived, l.total_recovered_derived,
        l.total_repayment_derived, l.total_writtenoff_derived, l.withdrawnon_date,
        l.writtenoffon_date
    FROM m_loan l
    LEFT JOIN m_client bc ON bc.id = l.client_id
    LEFT JOIN m_group bg ON bg.id = l.group_id
    WHERE (l.product_id = '${loanProductId}' OR '-1' = '${loanProductId}')
        AND (IFNULL(l.loan_officer_id, -10) = '${loanOfficerId}' OR '-1' = '${loanOfficerId}')
        AND (IFNULL(l.fund_id, -10) = ${fundId} OR -1 = ${fundId})
        AND (IFNULL(l.loanpurpose_cv_id, -10) = ${loanPurposeId} OR -1 = ${loanPurposeId})
        AND (l.currency_code = '${currencyId}' OR '-1' = '${currencyId}')
        AND l.disbursedon_date IS NOT NULL
        AND l.disbursedon_date <= DATE('${endDate}')
        AND COALESCE(l.office_id, bc.office_id, bg.office_id) IN (SELECT office_id FROM scope)
),
tx_asat AS (
    SELECT t.loan_id,
        SUM(CASE WHEN t.transaction_type_enum IN (2,5,6,28) THEN IFNULL(t.principal_portion_derived,0) ELSE 0 END)
          - SUM(CASE WHEN t.transaction_type_enum = 1 THEN IFNULL(t.amount,0) ELSE 0 END) AS p_adj,
        SUM(CASE WHEN t.transaction_type_enum IN (2,4,5,6,9,28,30) THEN IFNULL(t.interest_portion_derived,0) ELSE 0 END) AS i_adj,
        SUM(CASE WHEN t.transaction_type_enum IN (2,5,6,9,28) THEN IFNULL(t.fee_charges_portion_derived,0) ELSE 0 END) AS f_adj,
        SUM(CASE WHEN t.transaction_type_enum IN (2,5,6,9,28) THEN IFNULL(t.penalty_charges_portion_derived,0) ELSE 0 END) AS pen_adj,
        SUM(IFNULL(t.overpayment_portion_derived,0))
          - SUM(CASE WHEN t.transaction_type_enum IN (16,18,20,26,27) THEN IFNULL(t.amount,0) ELSE 0 END) AS ovp_adj
    FROM m_loan_transaction t
    JOIN base_all b ON b.id = t.loan_id
    WHERE t.is_reversed = 0
        AND t.transaction_date > DATE('${endDate}')
        AND ${loanStatusId} <> -1
    GROUP BY t.loan_id
),
cand AS (
    SELECT b.id,
        CASE
            WHEN b.loan_status_id IN (303,304) THEN b.loan_status_id
            WHEN b.loan_status_id = 400
                 AND IFNULL(COALESCE(b.withdrawnon_date, b.closedon_date), DATE('${endDate}'))
                     <= DATE('${endDate}') THEN 400
            WHEN b.loan_status_id = 500
                 AND IFNULL(COALESCE(b.rejectedon_date, b.closedon_date), DATE('${endDate}'))
                     <= DATE('${endDate}') THEN 500
            WHEN b.loan_status_id IN (100,200) AND b.disbursedon_date IS NULL THEN b.loan_status_id
            WHEN b.writtenoffon_date IS NOT NULL AND b.writtenoffon_date <= DATE('${endDate}')
                 AND IFNULL(b.total_writtenoff_derived,0) > 0 THEN 601
            WHEN GREATEST(IFNULL(b.principal_outstanding_derived,0) + IFNULL(pc.p_adj,0), 0)
               + GREATEST(IFNULL(b.interest_outstanding_derived,0) + IFNULL(pc.i_adj,0), 0)
               + GREATEST(IFNULL(b.fee_charges_outstanding_derived,0) + IFNULL(pc.f_adj,0), 0)
               + GREATEST(IFNULL(b.penalty_charges_outstanding_derived,0) + IFNULL(pc.pen_adj,0), 0) > 0.005 THEN 300
            WHEN GREATEST(IFNULL(b.total_overpaid_derived,0) - IFNULL(pc.ovp_adj,0), 0) > 0.005 THEN 700
            WHEN b.loan_status_id = 602 THEN 602
            ELSE 600
        END AS st_id
    FROM base_all b LEFT JOIN tx_asat pc ON pc.loan_id = b.id
    WHERE ${loanStatusId} <> -1
),
base AS (
    SELECT b.* FROM base_all b
    LEFT JOIN cand c ON c.id = b.id
    WHERE (${loanStatusId} = -1 OR c.st_id = ${loanStatusId})
    ORDER BY b.id
    LIMIT ${limit} OFFSET ${offset}
),
bid AS (SELECT id FROM base),
tx AS (
    SELECT t.loan_id,
        SUM(CASE WHEN t.transaction_date > DATE('${endDate}') AND t.transaction_type_enum IN (2,5,6,28) THEN IFNULL(t.principal_portion_derived,0) ELSE 0 END)
          - SUM(CASE WHEN t.transaction_date > DATE('${endDate}') AND t.transaction_type_enum = 1 THEN IFNULL(t.amount,0) ELSE 0 END) AS p_adj,
        SUM(CASE WHEN t.transaction_date > DATE('${endDate}') AND t.transaction_type_enum IN (2,4,5,6,9,28,30) THEN IFNULL(t.interest_portion_derived,0) ELSE 0 END) AS i_adj,
        SUM(CASE WHEN t.transaction_date > DATE('${endDate}') AND t.transaction_type_enum IN (2,5,6,9,28) THEN IFNULL(t.fee_charges_portion_derived,0) ELSE 0 END) AS f_adj,
        SUM(CASE WHEN t.transaction_date > DATE('${endDate}') AND t.transaction_type_enum IN (2,5,6,9,28) THEN IFNULL(t.penalty_charges_portion_derived,0) ELSE 0 END) AS pen_adj,
        SUM(CASE WHEN t.transaction_date > DATE('${endDate}') THEN IFNULL(t.overpayment_portion_derived,0) ELSE 0 END)
          - SUM(CASE WHEN t.transaction_date > DATE('${endDate}') AND t.transaction_type_enum IN (16,18,20,26,27) THEN IFNULL(t.amount,0) ELSE 0 END) AS ovp_adj,
        SUM(CASE WHEN t.transaction_date > DATE('${endDate}') AND t.transaction_type_enum = 2 THEN IFNULL(t.principal_portion_derived,0) ELSE 0 END) AS p_paid_adj,
        SUM(CASE WHEN t.transaction_date > DATE('${endDate}') AND t.transaction_type_enum IN (2,5) THEN IFNULL(t.interest_portion_derived,0) ELSE 0 END) AS i_paid_adj,
        SUM(CASE WHEN t.transaction_date > DATE('${endDate}') AND t.transaction_type_enum IN (2,5) THEN IFNULL(t.fee_charges_portion_derived,0) ELSE 0 END) AS f_paid_adj,
        SUM(CASE WHEN t.transaction_date > DATE('${endDate}') AND t.transaction_type_enum IN (2,5) THEN IFNULL(t.penalty_charges_portion_derived,0) ELSE 0 END) AS pen_paid_adj,
        SUM(CASE WHEN t.transaction_date > DATE('${endDate}') AND t.transaction_type_enum = 2 THEN IFNULL(t.amount,0) ELSE 0 END) AS actual_paid_adj,
        SUM(CASE WHEN t.transaction_date > DATE('${endDate}') AND t.transaction_type_enum = 4 THEN IFNULL(t.interest_portion_derived,0) ELSE 0 END) AS i_waived_adj,
        SUM(CASE WHEN t.transaction_date > DATE('${endDate}') AND t.transaction_type_enum = 9 THEN IFNULL(t.fee_charges_portion_derived,0) ELSE 0 END) AS f_waived_adj,
        SUM(CASE WHEN t.transaction_date > DATE('${endDate}') AND t.transaction_type_enum = 9 THEN IFNULL(t.penalty_charges_portion_derived,0) ELSE 0 END) AS pen_waived_adj,
        SUM(CASE WHEN t.transaction_date > DATE('${endDate}') AND t.transaction_type_enum = 8 THEN IFNULL(t.amount,0) ELSE 0 END) AS recovered_adj,
        SUM(CASE WHEN t.transaction_date > DATE('${endDate}') AND t.transaction_type_enum = 6 THEN IFNULL(t.amount,0) ELSE 0 END) AS writtenoff_adj,
        SUM(CASE WHEN t.transaction_date > DATE('${endDate}') AND t.transaction_type_enum = 1 THEN IFNULL(t.amount,0) ELSE 0 END) AS disb_adj,
        MAX(CASE WHEN t.transaction_type_enum = 30 THEN t.transaction_date END) AS cancelled_on,
        MAX(CASE WHEN t.transaction_type_enum IN (2,5,8,28) THEN t.transaction_date END) AS settled_on,
        SUM(CASE WHEN t.transaction_date <= DATE('${endDate}') THEN IFNULL(t.overpayment_portion_derived,0) ELSE 0 END) AS excess_paid,
        MAX(CASE WHEN t.transaction_date <= DATE('${endDate}') AND IFNULL(t.overpayment_portion_derived,0) > 0 THEN t.transaction_date END) AS last_excess_date,
        SUM(CASE WHEN t.transaction_date <= DATE('${endDate}') AND t.transaction_type_enum = 10 THEN IFNULL(t.interest_portion_derived,0) ELSE 0 END) AS accrued_int,
        MAX(CASE WHEN t.transaction_date <= DATE('${endDate}') AND t.transaction_type_enum IN (2,28)
                 THEN CONCAT(LPAD(TO_DAYS(t.transaction_date),7,'0'), LPAD(t.id,20,'0')) END) AS lp_key
    FROM m_loan_transaction t
    JOIN bid b ON b.id = t.loan_id
    WHERE t.is_reversed = 0
    GROUP BY t.loan_id
),
lp AS (
    SELECT tx.loan_id,
           MAX(t.transaction_date) AS transaction_date,
           MAX(t.amount) AS amount,
           MAX(t.principal_portion_derived) AS principal_portion_derived,
           MAX(t.interest_portion_derived) AS interest_portion_derived,
           MAX(t.fee_charges_portion_derived) AS fee_charges_portion_derived,
           MAX(t.penalty_charges_portion_derived) AS penalty_charges_portion_derived,
           MAX(t.overpayment_portion_derived) AS overpayment_portion_derived
    FROM tx
    JOIN m_loan_transaction t ON t.id = CAST(SUBSTRING(tx.lp_key, 8) AS UNSIGNED)
    WHERE tx.lp_key IS NOT NULL
    GROUP BY tx.loan_id
),
sp AS (
    SELECT m.loan_repayment_schedule_id AS sid,
        SUM(CASE WHEN t.transaction_type_enum <> 4 THEN IFNULL(m.principal_portion_derived,0) ELSE 0 END) AS p_after,
        SUM(CASE WHEN t.transaction_type_enum <> 4 THEN IFNULL(m.interest_portion_derived,0) ELSE 0 END) AS i_after,
        SUM(CASE WHEN t.transaction_type_enum <> 4 THEN IFNULL(m.fee_charges_portion_derived,0) ELSE 0 END) AS f_after,
        SUM(CASE WHEN t.transaction_type_enum <> 4 THEN IFNULL(m.penalty_charges_portion_derived,0) ELSE 0 END) AS pen_after,
        SUM(CASE WHEN t.transaction_type_enum = 4 THEN IFNULL(m.interest_portion_derived,0) ELSE 0 END) AS iw_after
    FROM m_loan_transaction t
    JOIN bid b ON b.id = t.loan_id
    JOIN m_loan_transaction_repayment_schedule_mapping m ON m.loan_transaction_id = t.id
    WHERE t.is_reversed = 0 AND t.transaction_date > DATE('${endDate}')
    GROUP BY m.loan_repayment_schedule_id
),
wf AS (
    SELECT b.id AS loan_id,
        CASE WHEN b.writtenoffon_date IS NOT NULL AND b.writtenoffon_date <= DATE('${endDate}')
                  AND IFNULL(b.total_writtenoff_derived,0) > 0 THEN 1 ELSE 0 END AS wo_on,
        CASE WHEN COALESCE(tx.cancelled_on, b.writtenoffon_date, b.closedon_date,
                           tx.settled_on, DATE(b.last_modified_on_utc))
                  <= DATE('${endDate}') THEN 1 ELSE 0 END AS ic_on
    FROM base b
    LEFT JOIN tx ON tx.loan_id = b.id
),
cwt AS (
    SELECT cpb.loan_charge_id AS cid, cpb.installment_number AS inst_no,
           SUM(IFNULL(cpb.amount,0)) AS amt
    FROM m_loan_transaction t
    JOIN bid b ON b.id = t.loan_id
    JOIN m_loan_charge_paid_by cpb ON cpb.loan_transaction_id = t.id
    WHERE t.is_reversed = 0 AND t.transaction_type_enum = 9
        AND t.transaction_date > DATE('${endDate}')
    GROUP BY cpb.loan_charge_id, cpb.installment_number
),
cwc AS (SELECT cid FROM cwt GROUP BY cid),
cmap AS (
    SELECT lc.id AS cid, lc.is_penalty, lic.loan_schedule_id AS sid, lrs.installment AS inst_no
    FROM cwc w
    JOIN m_loan_charge lc ON lc.id = w.cid AND lc.is_active = 1
    JOIN m_loan_installment_charge lic ON lic.loan_charge_id = lc.id
    JOIN m_loan_repayment_schedule lrs ON lrs.id = lic.loan_schedule_id
    UNION ALL
    SELECT lc.id, lc.is_penalty, lrs.id, lrs.installment
    FROM cwc w
    JOIN m_loan_charge lc ON lc.id = w.cid AND lc.is_active = 1
    JOIN m_loan_repayment_schedule lrs ON lrs.loan_id = lc.loan_id
        AND lc.due_for_collection_as_of_date >  lrs.fromdate
        AND lc.due_for_collection_as_of_date <= lrs.duedate
    WHERE NOT EXISTS (SELECT 1 FROM m_loan_installment_charge x WHERE x.loan_charge_id = lc.id)
),
cmapn AS (SELECT cid, COUNT(*) AS n FROM cmap GROUP BY cid),
cw AS (
    SELECT cm.sid,
        SUM(CASE WHEN cm.is_penalty = 0 THEN w.amt / IF(w.inst_no IS NULL, cn.n, 1) ELSE 0 END) AS f_waived_after,
        SUM(CASE WHEN cm.is_penalty = 1 THEN w.amt / IF(w.inst_no IS NULL, cn.n, 1) ELSE 0 END) AS pen_waived_after
    FROM cwt w
    JOIN cmap cm ON cm.cid = w.cid AND (w.inst_no IS NULL OR cm.inst_no = w.inst_no)
    JOIN cmapn cn ON cn.cid = w.cid
    GROUP BY cm.sid
),
sch AS (
    SELECT lrs.loan_id, lrs.installment, lrs.duedate,
        IFNULL(lrs.principal_amount,0) + IFNULL(lrs.interest_amount,0)
          + IFNULL(lrs.fee_charges_amount,0) + IFNULL(lrs.penalty_charges_amount,0) AS due_total,
        IFNULL(lrs.principal_amount,0) AS due_p,
        IFNULL(lrs.interest_amount,0) AS due_i,
        IFNULL(lrs.fee_charges_amount,0) AS due_f,
        IFNULL(lrs.penalty_charges_amount,0) AS due_pen,
        GREATEST(IFNULL(lrs.principal_completed_derived,0) - IFNULL(sp.p_after,0), 0) AS paid_p,
        GREATEST(IFNULL(lrs.interest_completed_derived,0) - IFNULL(sp.i_after,0), 0) AS paid_i,
        GREATEST(IFNULL(lrs.fee_charges_completed_derived,0) - IFNULL(sp.f_after,0), 0) AS paid_f,
        GREATEST(IFNULL(lrs.penalty_charges_completed_derived,0) - IFNULL(sp.pen_after,0), 0) AS paid_pen,
        GREATEST(IFNULL(lrs.principal_completed_derived,0) - IFNULL(sp.p_after,0), 0)
          + wf.wo_on * IFNULL(lrs.principal_writtenoff_derived,0) AS rel_p,
        GREATEST(IFNULL(lrs.interest_completed_derived,0) - IFNULL(sp.i_after,0), 0)
          + GREATEST(IFNULL(lrs.interest_waived_derived,0) - IFNULL(sp.iw_after,0), 0)
          + wf.ic_on * IFNULL(lrs.interest_cancelled_derived,0)
          + wf.wo_on * IFNULL(lrs.interest_writtenoff_derived,0) AS rel_i,
        GREATEST(IFNULL(lrs.fee_charges_completed_derived,0) - IFNULL(sp.f_after,0), 0)
          + GREATEST(IFNULL(lrs.fee_charges_waived_derived,0) - IFNULL(cw.f_waived_after,0), 0)
          + wf.wo_on * IFNULL(lrs.fee_charges_writtenoff_derived,0) AS rel_f,
        GREATEST(IFNULL(lrs.penalty_charges_completed_derived,0) - IFNULL(sp.pen_after,0), 0)
          + GREATEST(IFNULL(lrs.penalty_charges_waived_derived,0) - IFNULL(cw.pen_waived_after,0), 0)
          + wf.wo_on * IFNULL(lrs.penalty_charges_writtenoff_derived,0) AS rel_pen
    FROM m_loan_repayment_schedule lrs
    JOIN wf ON wf.loan_id = lrs.loan_id
    LEFT JOIN sp ON sp.sid = lrs.id
    LEFT JOIN cw ON cw.sid = lrs.id
),
schx AS (
    SELECT s.loan_id, s.duedate, s.due_p, s.due_i, s.due_f, s.due_pen,
        s.paid_p, s.paid_i, s.paid_f, s.paid_pen,
        s.rel_p, s.rel_i, s.rel_f, s.rel_pen,
        ROUND(s.due_p + s.due_i, 2) AS emi_val,
        (s.due_total - s.rel_p - s.rel_i - s.rel_f - s.rel_pen) > 0.005 AS unpaid,
        ROW_NUMBER() OVER (
            PARTITION BY s.loan_id, (s.due_total - s.rel_p - s.rel_i - s.rel_f - s.rel_pen) > 0.005
            ORDER BY s.duedate, s.installment) AS urn
    FROM sch s
),
sagg AS (
    SELECT loan_id,
        SUM(CASE WHEN duedate <= DATE('${endDate}') AND unpaid
                 THEN GREATEST(due_p - rel_p, 0) + GREATEST(due_i - rel_i, 0)
                    + GREATEST(due_f - rel_f, 0) + GREATEST(due_pen - rel_pen, 0) ELSE 0 END) AS past_due,
        SUM(CASE WHEN duedate <= DATE('${endDate}') AND unpaid THEN GREATEST(due_p - rel_p, 0) ELSE 0 END) AS past_due_p,
        SUM(CASE WHEN duedate <= DATE('${endDate}') AND unpaid THEN GREATEST(due_i - rel_i, 0) ELSE 0 END) AS past_due_i,
        SUM(CASE WHEN duedate <= DATE('${endDate}') AND unpaid THEN GREATEST(due_f - rel_f, 0) ELSE 0 END) AS past_due_f,
        SUM(CASE WHEN duedate <= DATE('${endDate}') AND unpaid THEN GREATEST(due_pen - rel_pen, 0) ELSE 0 END) AS past_due_pen,
        SUM(CASE WHEN duedate <= DATE('${endDate}') AND unpaid THEN 1 ELSE 0 END) AS inst_in_arrears,
        MIN(CASE WHEN duedate <= DATE('${endDate}') AND unpaid THEN duedate END) AS oldest_overdue,
        SUM(CASE WHEN duedate > DATE('${endDate}') THEN paid_p + paid_i + paid_f + paid_pen ELSE 0 END) AS advance_bal,
        MAX(duedate) AS final_due,
        MAX(CASE WHEN unpaid AND urn = 1 THEN duedate END) AS next_due,
        MAX(CASE WHEN unpaid AND urn = 1 THEN GREATEST(due_p - rel_p, 0) END) AS rem_p,
        MAX(CASE WHEN unpaid AND urn = 1 THEN GREATEST(due_i - rel_i, 0) END) AS rem_i,
        MAX(CASE WHEN unpaid AND urn = 1 THEN GREATEST(due_f - rel_f, 0) END) AS rem_f,
        MAX(CASE WHEN unpaid AND urn = 1 THEN GREATEST(due_p - rel_p, 0) + GREATEST(due_i - rel_i, 0)
                    + GREATEST(due_f - rel_f, 0) + GREATEST(due_pen - rel_pen, 0) END) AS rem_total
    FROM schx
    GROUP BY loan_id
),
emi AS (
    SELECT loan_id, emi FROM (
        SELECT loan_id, emi_val AS emi,
            ROW_NUMBER() OVER (PARTITION BY loan_id ORDER BY COUNT(*) DESC, emi_val DESC) rn
        FROM schx GROUP BY loan_id, emi_val
    ) z WHERE rn = 1
),
chg AS (
    SELECT lc.loan_id,
        SUM(CASE WHEN lc.is_penalty = 0 THEN IFNULL(lc.amount,0) ELSE 0 END) AS fee_charged,
        SUM(CASE WHEN lc.is_penalty = 1 THEN IFNULL(lc.amount,0) ELSE 0 END) AS pen_charged
    FROM m_loan_charge lc
    JOIN bid b ON b.id = lc.loan_id
    WHERE lc.is_active = 1
        AND (lc.due_for_collection_as_of_date IS NULL
             OR lc.due_for_collection_as_of_date <= DATE('${endDate}'))
    GROUP BY lc.loan_id
),
addr AS (
    SELECT client_id, physical_address_sector, physical_address_cell, physical_address_district,
           state_province_id, location
    FROM (
        SELECT ca.client_id, a.physical_address_sector, a.physical_address_cell, a.physical_address_district,
               a.state_province_id, a.location,
               ROW_NUMBER() OVER (PARTITION BY ca.client_id
                   ORDER BY ca.is_active DESC, COALESCE(a.updated_on, a.created_on) DESC, a.id DESC) rn
        FROM m_client_address ca JOIN m_address a ON a.id = ca.address_id
    ) z WHERE rn = 1
),
coi1 AS (
    SELECT client_id, national_identification_number, telephone_no, nationality_cv_id, strata_cv_id FROM (
        SELECT x.client_id, x.national_identification_number, x.telephone_no, x.nationality_cv_id, x.strata_cv_id,
               ROW_NUMBER() OVER (PARTITION BY x.client_id ORDER BY x.id DESC) rn FROM m_client_other_info x
    ) z WHERE rn = 1
),
crs1 AS (
    SELECT client_id, cohort_cv_id FROM (
        SELECT x.client_id, x.cohort_cv_id,
               ROW_NUMBER() OVER (PARTITION BY x.client_id ORDER BY x.id DESC) rn FROM m_client_recruitment_survey x
    ) z WHERE rn = 1
),
bi1 AS (
    SELECT client_id, `Business Sector` AS sector, `Business Sub-Sector` AS subsector FROM (
        SELECT x.client_id, x.`Business Sector`, x.`Business Sub-Sector`,
               ROW_NUMBER() OVER (PARTITION BY x.client_id ORDER BY x.id DESC) rn FROM `Business Information` x
    ) z WHERE rn = 1
),
cai1 AS (
    SELECT client_id, alt_phone_no FROM (
        SELECT x.client_id, x.alt_phone_no,
               ROW_NUMBER() OVER (PARTITION BY x.client_id ORDER BY x.id DESC) rn FROM m_client_additional_info x
    ) z WHERE rn = 1
),
cid1 AS (
    SELECT client_id, incorp_no FROM (
        SELECT x.client_id, x.incorp_no,
               ROW_NUMBER() OVER (PARTITION BY x.client_id ORDER BY x.id DESC) rn FROM m_client_non_person x
    ) z WHERE rn = 1
),
ebi1 AS (
    SELECT client_id, `BusinessSectors_cd_Business Sector` AS sector_cv, `BusinessSubSectors_cd_Business Sub-Sector` AS subsector_cv FROM (
        SELECT x.client_id, x.`BusinessSectors_cd_Business Sector`, x.`BusinessSubSectors_cd_Business Sub-Sector`,
               ROW_NUMBER() OVER (PARTITION BY x.client_id ORDER BY x.client_id) rn FROM `Entity Business Information` x
    ) z WHERE rn = 1
),
boi1 AS (
    SELECT client_id, `Business Owner Date of Birth` AS owner_dob, `Gender_cd_Gender` AS gender_cv, `COUNTRY_cd_Nationality` AS nationality_cv FROM (
        SELECT x.client_id, x.`Business Owner Date of Birth`, x.`Gender_cd_Gender`, x.`COUNTRY_cd_Nationality`,
               ROW_NUMBER() OVER (PARTITION BY x.client_id ORDER BY x.client_id) rn FROM `Business Owner Information` x
    ) z WHERE rn = 1
),
kbd1 AS (
    SELECT loan_id, `KivaSector_cd_Sector` AS kiva_sector_cv, `KivaActivity_cd_Activity` AS kiva_activity_cv FROM (
        SELECT x.loan_id, x.`KivaSector_cd_Sector`, x.`KivaActivity_cd_Activity`,
               ROW_NUMBER() OVER (PARTITION BY x.loan_id ORDER BY x.loan_id) rn FROM `Kiva Business Details` x
    ) z WHERE rn = 1
),
ldd AS (
    SELECT loan_id, MAX(mfi_code) AS mfi_code FROM m_loan_disbursement_detail
    WHERE mfi_code IS NOT NULL AND mfi_code <> '' GROUP BY loan_id
),
cyc AS (
    SELECT id AS loan_id, CAST(ROW_NUMBER() OVER (PARTITION BY client_id ORDER BY submittedon_date, id) AS SIGNED) AS cycle
    FROM m_loan WHERE loan_status_id NOT IN (100,400,500) AND submittedon_date <= DATE('${endDate}')
),
asat AS (
    SELECT b.id AS loan_id,
        GREATEST(IFNULL(b.principal_outstanding_derived,0) + IFNULL(pc.p_adj,0), 0) AS p_bal,
        GREATEST(IFNULL(b.interest_outstanding_derived,0) + IFNULL(pc.i_adj,0), 0) AS i_bal,
        GREATEST(IFNULL(b.fee_charges_outstanding_derived,0) + IFNULL(pc.f_adj,0), 0) AS f_bal,
        GREATEST(IFNULL(b.penalty_charges_outstanding_derived,0) + IFNULL(pc.pen_adj,0), 0) AS pen_bal,
        GREATEST(IFNULL(b.total_overpaid_derived,0) - IFNULL(pc.ovp_adj,0), 0) AS ovp_bal,
        GREATEST(IFNULL(b.principal_repaid_derived,0) - IFNULL(pc.p_paid_adj,0), 0) AS p_paid,
        GREATEST(IFNULL(b.interest_repaid_derived,0) - IFNULL(pc.i_paid_adj,0), 0) AS i_paid,
        GREATEST(IFNULL(b.fee_charges_repaid_derived,0) - IFNULL(pc.f_paid_adj,0), 0) AS f_paid,
        GREATEST(IFNULL(b.penalty_charges_repaid_derived,0) - IFNULL(pc.pen_paid_adj,0), 0) AS pen_paid,
        GREATEST(IFNULL(b.total_repayment_derived,0) - IFNULL(pc.actual_paid_adj,0), 0) AS actual_paid,
        GREATEST(IFNULL(b.interest_waived_derived,0) - IFNULL(pc.i_waived_adj,0), 0) AS i_waived,
        GREATEST(IFNULL(b.fee_charges_waived_derived,0) - IFNULL(pc.f_waived_adj,0), 0) AS f_waived,
        GREATEST(IFNULL(b.penalty_charges_waived_derived,0) - IFNULL(pc.pen_waived_adj,0), 0) AS pen_waived,
        GREATEST(IFNULL(b.total_recovered_derived,0) - IFNULL(pc.recovered_adj,0), 0) AS recovered,
        CASE WHEN b.writtenoffon_date IS NOT NULL AND b.writtenoffon_date <= DATE('${endDate}')
                      AND IFNULL(b.total_writtenoff_derived,0) > 0
             THEN GREATEST(IFNULL(b.total_writtenoff_derived,0) - IFNULL(pc.writtenoff_adj,0), 0) ELSE 0 END AS writtenoff,
        (b.writtenoffon_date IS NOT NULL AND b.writtenoffon_date <= DATE('${endDate}')
              AND IFNULL(b.total_writtenoff_derived,0) > 0) AS is_wo,
        GREATEST(IFNULL(b.principal_disbursed_derived,0) - IFNULL(pc.disb_adj,0), 0) AS disbursed,
        (b.rescheduledon_date IS NOT NULL AND b.rescheduledon_date <= DATE('${endDate}')) AS is_resched,
        CASE
            WHEN b.loan_status_id IN (303,304) THEN b.loan_status_id
            WHEN b.loan_status_id = 400
                 AND IFNULL(COALESCE(b.withdrawnon_date, b.closedon_date), DATE('${endDate}'))
                     <= DATE('${endDate}') THEN 400
            WHEN b.loan_status_id = 500
                 AND IFNULL(COALESCE(b.rejectedon_date, b.closedon_date), DATE('${endDate}'))
                     <= DATE('${endDate}') THEN 500
            WHEN b.loan_status_id IN (100,200) AND b.disbursedon_date IS NULL THEN b.loan_status_id
            WHEN b.writtenoffon_date IS NOT NULL AND b.writtenoffon_date <= DATE('${endDate}')
                 AND IFNULL(b.total_writtenoff_derived,0) > 0 THEN 601
            WHEN GREATEST(IFNULL(b.principal_outstanding_derived,0) + IFNULL(pc.p_adj,0), 0)
               + GREATEST(IFNULL(b.interest_outstanding_derived,0) + IFNULL(pc.i_adj,0), 0)
               + GREATEST(IFNULL(b.fee_charges_outstanding_derived,0) + IFNULL(pc.f_adj,0), 0)
               + GREATEST(IFNULL(b.penalty_charges_outstanding_derived,0) + IFNULL(pc.pen_adj,0), 0) > 0.005 THEN 300
            WHEN GREATEST(IFNULL(b.total_overpaid_derived,0) - IFNULL(pc.ovp_adj,0), 0) > 0.005 THEN 700
            WHEN b.loan_status_id = 602 THEN 602
            ELSE 600
        END AS st_id
    FROM base b LEFT JOIN tx pc ON pc.loan_id = b.id
)
SELECT
    DATE('${endDate}') AS 'As of Date',
    (SELECT o2.name FROM m_office o2 WHERE o2.id = ${officeId}) AS 'Office',
    NOW() AS 'Generated On',
    (SELECT CONCAT(au.firstname, ' ', au.lastname) FROM m_appuser au WHERE au.id = ${currentUserId}) AS 'Generated By',

    b.id,
    b.account_no AS 'Loan Number',
    p.name AS 'Loan Product',
    CASE b.loan_type_enum WHEN 1 THEN 'Individual' WHEN 2 THEN 'Group' WHEN 3 THEN 'JLG'
        WHEN 4 THEN 'GLIM' WHEN 5 THEN 'GSIM' END AS 'Loan Type',
    loanPurposeTble.code_value AS 'Purpose',
    cvd.code_value AS 'Department',
    coalesce(cvs.code_value, '') AS 'Strata',
    f.name AS 'Funder',
    COALESCE(ldd.mfi_code, '') AS 'MFI Code',
    b.kiva_id AS 'KIVA Loan ID',
    cyc.cycle AS 'Cycle',
    st.display_name AS 'Loan Officer',

    CASE WHEN c.legal_form_enum = 1
         THEN CONCAT(c.firstname, ' ', IFNULL(c.middlename,''), ' ', c.lastname)
         ELSE COALESCE(c.display_name, g.display_name) END AS 'Client Name',
    c.external_id AS 'Client UUID',
    CASE c.legal_form_enum WHEN 1 THEN 'Individual' WHEN 2 THEN 'Entity' ELSE 'Group' END AS 'Client Type',
    CASE c.legal_form_enum WHEN 1 THEN COALESCE(coi1.national_identification_number, 'NA')
         ELSE COALESCE(cid.incorp_no, 'NA') END AS 'Client ID',
    CASE WHEN c.legal_form_enum = 2 THEN boi.owner_dob
         ELSE c.date_of_birth END AS 'Date of Birth',
    CASE WHEN c.legal_form_enum = 2 THEN cvbog.code_value ELSE cvg.code_value END AS 'Gender',
    CASE WHEN c.legal_form_enum = 2 THEN cvbon.code_value ELSE cvn.code_value END AS 'Nationality',
    c.kiva_id AS 'KIVA Client ID',
    cvc.code_value AS 'Cohort',
    coi1.telephone_no AS 'Telephone',
    cai.alt_phone_no AS 'Mobile No',
    cvp.code_value AS 'Province',
    adr.physical_address_district AS 'District',
    adr.physical_address_sector AS 'Sector',
    adr.physical_address_cell AS 'Cell',
    adr.location AS 'Location',
    b.description AS 'Business Description',
    CASE WHEN c.legal_form_enum = 2 THEN cvebis.code_value ELSE bi1.sector END AS 'Industry/Sector of Activity',
    CASE WHEN c.legal_form_enum = 2 THEN cvebiss.code_value ELSE bi1.subsector END AS 'Business Sub-Sector',
    cvks.code_value AS 'KIVA Business Sector',
    cvka.code_value AS 'KIVA Business Activity',

    b.submittedon_date AS 'Submission Date',
    b.approvedon_date AS 'Approval Date',
    b.disbursedon_date AS 'Disbursement Date',
    b.principal_amount_proposed AS 'Applied Amount',
    b.approved_principal AS 'Approved Amount',
    a.disbursed AS 'Disbursed Amount',
    (b.approved_principal - a.disbursed) AS 'Difference',
    currency.name AS 'Currency Type',
    CASE b.repayment_period_frequency_enum WHEN 0 THEN 'Daily' WHEN 1 THEN 'Weekly'
        WHEN 2 THEN 'Monthly' WHEN 3 THEN 'Yearly' END AS 'Re-payment Term',
    b.term_frequency AS 'Terms Duration',
    IFNULL(emi.emi, 0) AS 'EMI',

    CASE WHEN a.is_wo THEN 0 ELSE ROUND(a.p_bal + a.i_bal + a.f_bal + a.pen_bal, 6) END AS 'Current Balance',
    CASE WHEN a.is_wo THEN 0 ELSE a.p_bal END AS 'Principal Balance',
    CASE WHEN a.is_wo THEN 0 ELSE a.i_bal END AS 'Interest Balance',
    CASE WHEN a.is_wo THEN 0 ELSE a.f_bal END AS 'Fee balance',
    CASE WHEN a.is_wo THEN 0 ELSE a.pen_bal END AS 'Penalty Balance',
    a.ovp_bal AS 'Excess Balance',
    CASE WHEN a.is_wo THEN 0 ELSE IFNULL(sagg.advance_bal, 0) END AS 'Advance Payment Balance',
    a.writtenoff AS 'Written-Off Amount',

    CASE WHEN a.is_wo THEN 0 ELSE IFNULL(sagg.past_due, 0) END AS 'Amount Past Due',
    CASE WHEN a.is_wo THEN 0 ELSE IFNULL(sagg.past_due_p, 0) END AS 'Principal Past Due',
    CASE WHEN a.is_wo THEN 0 ELSE IFNULL(sagg.past_due_i, 0) END AS 'Interest Past Due',
    CASE WHEN a.is_wo THEN 0 ELSE IFNULL(sagg.past_due_pen, 0) END AS 'Penalties Past Due',
    CASE WHEN a.is_wo THEN 0 ELSE IFNULL(sagg.past_due_f, 0) END AS 'Fees Past Due',
    CASE WHEN a.is_wo THEN 0 ELSE GREATEST(IFNULL(DATEDIFF(DATE('${endDate}'), sagg.oldest_overdue), 0), 0) END AS 'Days in Arrears',
    CASE WHEN a.is_wo THEN 0 ELSE IFNULL(sagg.inst_in_arrears, 0) END AS 'Installment in Arrears',

    a.actual_paid AS 'Actual Payment Amount',
    a.p_paid AS 'Principal Paid',
    a.i_paid AS 'Interest Paid',
    a.f_paid AS 'Insurance fee Paid',
    a.f_paid AS 'Fee Paid',
    a.pen_paid AS 'Penalty Paid',
    IFNULL(chg.pen_charged, 0) AS 'Total Late Fees',
    IFNULL(chg.fee_charged, 0) AS 'Insurance fee Charged',
    a.i_waived AS 'Interest Waived',
    a.f_waived AS 'Fee Waived',
    a.pen_waived AS 'Pen Waived',
    IFNULL(tx.excess_paid, 0) AS 'Excess Amount Paid',
    a.recovered AS 'Post-Write-Off Recovery',
    IFNULL(tx.accrued_int, 0) AS 'Interest As At',

    lp.transaction_date AS 'Last Payment Date',
    IFNULL(lp.amount, 0) AS 'Last Payment Amount',
    IFNULL(lp.principal_portion_derived, 0) AS 'Last Principal Amount',
    IFNULL(lp.interest_portion_derived, 0) AS 'Last Interest Amount',
    IFNULL(lp.fee_charges_portion_derived, 0) AS 'Last Fees Amount',
    IFNULL(lp.penalty_charges_portion_derived, 0) AS 'Last Late Fees Amount',
    IFNULL(lp.overpayment_portion_derived, 0) AS 'Last Excess Paid Amount',

    CASE WHEN a.is_wo OR (a.p_bal + a.i_bal + a.f_bal + a.pen_bal) <= 0.005 THEN NULL
         ELSE IFNULL(sagg.rem_p, 0) END AS 'Scheduled Principal Amount',
    CASE WHEN a.is_wo OR (a.p_bal + a.i_bal + a.f_bal + a.pen_bal) <= 0.005 THEN NULL
         ELSE IFNULL(sagg.rem_i, 0) END AS 'Scheduled Interest Amount',
    CASE WHEN a.is_wo OR (a.p_bal + a.i_bal + a.f_bal + a.pen_bal) <= 0.005 THEN NULL
         ELSE IFNULL(sagg.rem_f, 0) END AS 'Scheduled Fees Amount',
    CASE WHEN a.is_wo OR (a.p_bal + a.i_bal + a.f_bal + a.pen_bal) <= 0.005 THEN NULL
         ELSE IFNULL(sagg.rem_total, 0) END AS 'Scheduled Payment Amount',
    CASE WHEN a.is_wo OR (a.p_bal + a.i_bal + a.f_bal + a.pen_bal) <= 0.005 THEN NULL
         ELSE COALESCE(sagg.next_due, sagg.final_due, b.maturedon_date) END AS 'Next Payment Date',
    COALESCE(sagg.final_due, b.maturedon_date, b.expected_maturedon_date) AS 'Final Payment Date',
    CASE
        WHEN a.is_wo THEN b.writtenoffon_date
        WHEN (a.p_bal + a.i_bal + a.f_bal + a.pen_bal) > 0.005 THEN NULL
        WHEN a.ovp_bal > 0.005 THEN COALESCE(tx.last_excess_date, lp.transaction_date)
        WHEN b.loan_status_id = 602 AND a.is_resched THEN b.rescheduledon_date
        ELSE COALESCE(CASE WHEN b.closedon_date <= DATE('${endDate}') THEN b.closedon_date END,
                      lp.transaction_date, b.rescheduledon_date)
    END AS 'Date Closed',

    CASE
        WHEN a.st_id IN (100,200,400,500) AND con.enabled = TRUE
             AND b.loan_decision_state IS NOT NULL AND CASE b.loan_decision_state
             WHEN 1000 THEN 'Review Application' WHEN 1200 THEN 'Due Diligence'
             WHEN 1300 THEN 'Collateral Review' WHEN 1400 THEN 'IC Review Level One'
             WHEN 1500 THEN 'IC Review Level Two' WHEN 1600 THEN 'IC Review Level Three'
             WHEN 1700 THEN 'IC Review Level Four' WHEN 1800 THEN 'IC Review Level Five'
             WHEN 1900 THEN 'Prepare And Sign Contract' END <> 'Prepare And Sign Contract'
             THEN CASE b.loan_decision_state
             WHEN 1000 THEN 'Review Application' WHEN 1200 THEN 'Due Diligence'
             WHEN 1300 THEN 'Collateral Review' WHEN 1400 THEN 'IC Review Level One'
             WHEN 1500 THEN 'IC Review Level Two' WHEN 1600 THEN 'IC Review Level Three'
             WHEN 1700 THEN 'IC Review Level Four' WHEN 1800 THEN 'IC Review Level Five'
             WHEN 1900 THEN 'Prepare And Sign Contract' END
        WHEN a.st_id = 100 THEN 'Pending Approval'
        WHEN a.st_id = 200 THEN 'Approval'
        WHEN a.st_id = 400 THEN 'Withdrawn By Client'
        WHEN a.st_id = 500 THEN 'Rejected'
        WHEN a.st_id = 303 THEN 'Transfer In Progress'
        WHEN a.st_id = 304 THEN 'Transfer On Hold'
        WHEN a.st_id = 601 THEN 'Written Off'
        WHEN a.st_id = 300 AND DATEDIFF(DATE('${endDate}'), sagg.oldest_overdue) > 0 THEN 'Active / In Arrears'
        WHEN a.st_id = 300 THEN 'Active'
        WHEN a.st_id = 700 THEN 'Overpaid'
        WHEN a.st_id = 602 THEN 'Closed (Rescheduled)'
        ELSE 'Closed'
    END AS 'Loan Status',

    b.created_on_utc,
    b.last_modified_on_utc
FROM base b
JOIN asat a ON a.loan_id = b.id
JOIN m_product_loan p ON p.id = b.product_id
LEFT JOIN m_client c ON c.id = b.client_id
LEFT JOIN m_group g ON g.id = b.group_id
LEFT JOIN m_staff st ON st.id = b.loan_officer_id
LEFT JOIN m_currency currency ON currency.code = p.currency_code
LEFT JOIN m_fund f ON f.id = b.fund_id
LEFT JOIN m_code_value cvd ON cvd.id = b.department_cv_id
LEFT JOIN m_code_value loanPurposeTble ON loanPurposeTble.id = b.loanpurpose_cv_id
LEFT JOIN m_code_value cvg ON cvg.id = c.gender_cv_id
LEFT JOIN coi1 ON coi1.client_id = c.id
LEFT JOIN crs1 ON crs1.client_id = c.id
LEFT JOIN cai1 cai ON cai.client_id = c.id
LEFT JOIN m_code_value cvn ON cvn.id = coi1.nationality_cv_id
LEFT JOIN m_code_value cvs ON cvs.id = coi1.strata_cv_id
LEFT JOIN m_code_value cvc ON cvc.id = crs1.cohort_cv_id
LEFT JOIN cid1 cid ON cid.client_id = c.id
LEFT JOIN addr adr ON adr.client_id = c.id
LEFT JOIN m_code_value cvp ON cvp.id = adr.state_province_id
LEFT JOIN bi1 ON bi1.client_id = c.id
LEFT JOIN ebi1 ebi ON ebi.client_id = c.id
LEFT JOIN m_code_value cvebis ON cvebis.id = ebi.sector_cv
LEFT JOIN m_code_value cvebiss ON cvebiss.id = ebi.subsector_cv
LEFT JOIN boi1 boi ON boi.client_id = c.id
LEFT JOIN m_code_value cvbog ON cvbog.id = boi.gender_cv
LEFT JOIN m_code_value cvbon ON cvbon.id = boi.nationality_cv
LEFT JOIN kbd1 kbd ON kbd.loan_id = b.id
LEFT JOIN m_code_value cvks ON cvks.id = kbd.kiva_sector_cv
LEFT JOIN m_code_value cvka ON cvka.id = kbd.kiva_activity_cv
LEFT JOIN ldd ON ldd.loan_id = b.id
LEFT JOIN cyc ON cyc.loan_id = b.id
LEFT JOIN sagg ON sagg.loan_id = b.id
LEFT JOIN emi ON emi.loan_id = b.id
LEFT JOIN lp ON lp.loan_id = b.id
LEFT JOIN tx ON tx.loan_id = b.id
LEFT JOIN chg ON chg.loan_id = b.id
LEFT JOIN c_configuration con ON con.name = 'Add-More-Stages-To-A-Loan-Life-Cycle'
WHERE (${loanStatusId} = -1 OR a.st_id = ${loanStatusId})
ORDER BY b.id",
    report_count_sql = "WITH scope AS (
    SELECT ounder.id AS office_id
    FROM m_office o
    JOIN m_office ounder ON ounder.hierarchy LIKE CONCAT(o.hierarchy, '%')
        AND ounder.hierarchy LIKE CONCAT('${currentUserHierarchy}', '%')
    WHERE o.id = ${officeId}
),
base_all AS (
    SELECT l.id, l.account_no, l.approvedon_date, l.approved_principal, l.client_id,
        l.closedon_date, l.created_on_utc, l.department_cv_id, l.description,
        l.disbursedon_date, l.expected_maturedon_date, l.fee_charges_outstanding_derived,
        l.fee_charges_repaid_derived, l.fee_charges_waived_derived, l.fund_id, l.group_id,
        l.interest_outstanding_derived, l.interest_repaid_derived, l.interest_waived_derived,
        l.kiva_id, l.last_modified_on_utc, l.loan_decision_state, l.loan_officer_id,
        l.loanpurpose_cv_id, l.loan_status_id, l.loan_type_enum, l.maturedon_date,
        l.penalty_charges_outstanding_derived, l.penalty_charges_repaid_derived,
        l.penalty_charges_waived_derived, l.principal_amount_proposed,
        l.principal_disbursed_derived, l.principal_outstanding_derived,
        l.principal_repaid_derived, l.product_id, l.rejectedon_date,
        l.repayment_period_frequency_enum, l.rescheduledon_date, l.submittedon_date,
        l.term_frequency, l.total_overpaid_derived, l.total_recovered_derived,
        l.total_repayment_derived, l.total_writtenoff_derived, l.withdrawnon_date,
        l.writtenoffon_date
    FROM m_loan l
    LEFT JOIN m_client bc ON bc.id = l.client_id
    LEFT JOIN m_group bg ON bg.id = l.group_id
    WHERE (l.product_id = '${loanProductId}' OR '-1' = '${loanProductId}')
        AND (IFNULL(l.loan_officer_id, -10) = '${loanOfficerId}' OR '-1' = '${loanOfficerId}')
        AND (IFNULL(l.fund_id, -10) = ${fundId} OR -1 = ${fundId})
        AND (IFNULL(l.loanpurpose_cv_id, -10) = ${loanPurposeId} OR -1 = ${loanPurposeId})
        AND (l.currency_code = '${currencyId}' OR '-1' = '${currencyId}')
        AND l.disbursedon_date IS NOT NULL
        AND l.disbursedon_date <= DATE('${endDate}')
        AND COALESCE(l.office_id, bc.office_id, bg.office_id) IN (SELECT office_id FROM scope)
),
tx_asat AS (
    SELECT t.loan_id,
        SUM(CASE WHEN t.transaction_type_enum IN (2,5,6,28) THEN IFNULL(t.principal_portion_derived,0) ELSE 0 END)
          - SUM(CASE WHEN t.transaction_type_enum = 1 THEN IFNULL(t.amount,0) ELSE 0 END) AS p_adj,
        SUM(CASE WHEN t.transaction_type_enum IN (2,4,5,6,9,28,30) THEN IFNULL(t.interest_portion_derived,0) ELSE 0 END) AS i_adj,
        SUM(CASE WHEN t.transaction_type_enum IN (2,5,6,9,28) THEN IFNULL(t.fee_charges_portion_derived,0) ELSE 0 END) AS f_adj,
        SUM(CASE WHEN t.transaction_type_enum IN (2,5,6,9,28) THEN IFNULL(t.penalty_charges_portion_derived,0) ELSE 0 END) AS pen_adj,
        SUM(IFNULL(t.overpayment_portion_derived,0))
          - SUM(CASE WHEN t.transaction_type_enum IN (16,18,20,26,27) THEN IFNULL(t.amount,0) ELSE 0 END) AS ovp_adj
    FROM m_loan_transaction t
    JOIN base_all b ON b.id = t.loan_id
    WHERE t.is_reversed = 0
        AND t.transaction_date > DATE('${endDate}')
        AND ${loanStatusId} <> -1
    GROUP BY t.loan_id
),
cand AS (
    SELECT b.id,
        CASE
            WHEN b.loan_status_id IN (303,304) THEN b.loan_status_id
            WHEN b.loan_status_id = 400
                 AND IFNULL(COALESCE(b.withdrawnon_date, b.closedon_date), DATE('${endDate}'))
                     <= DATE('${endDate}') THEN 400
            WHEN b.loan_status_id = 500
                 AND IFNULL(COALESCE(b.rejectedon_date, b.closedon_date), DATE('${endDate}'))
                     <= DATE('${endDate}') THEN 500
            WHEN b.loan_status_id IN (100,200) AND b.disbursedon_date IS NULL THEN b.loan_status_id
            WHEN b.writtenoffon_date IS NOT NULL AND b.writtenoffon_date <= DATE('${endDate}')
                 AND IFNULL(b.total_writtenoff_derived,0) > 0 THEN 601
            WHEN GREATEST(IFNULL(b.principal_outstanding_derived,0) + IFNULL(pc.p_adj,0), 0)
               + GREATEST(IFNULL(b.interest_outstanding_derived,0) + IFNULL(pc.i_adj,0), 0)
               + GREATEST(IFNULL(b.fee_charges_outstanding_derived,0) + IFNULL(pc.f_adj,0), 0)
               + GREATEST(IFNULL(b.penalty_charges_outstanding_derived,0) + IFNULL(pc.pen_adj,0), 0) > 0.005 THEN 300
            WHEN GREATEST(IFNULL(b.total_overpaid_derived,0) - IFNULL(pc.ovp_adj,0), 0) > 0.005 THEN 700
            WHEN b.loan_status_id = 602 THEN 602
            ELSE 600
        END AS st_id
    FROM base_all b LEFT JOIN tx_asat pc ON pc.loan_id = b.id
    WHERE ${loanStatusId} <> -1
)
SELECT COUNT(*) AS 'Total Records'
FROM base_all b
LEFT JOIN cand c ON c.id = b.id
WHERE (${loanStatusId} = -1 OR c.st_id = ${loanStatusId})"
WHERE report_name = 'Portfolio Management';
