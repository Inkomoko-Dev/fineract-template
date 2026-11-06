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

-- Portfolio scan for the early-repayment allocation defect:
-- an earlier installment still has a balance while a later installment already holds an in-advance amount.
-- Run on a reporting replica, off peak. Amounts are in the loan currency.
--
-- earlier_outstanding ignores interest that was cancelled on an early settlement, so a genuine payoff
-- is not listed. arrears_sms_sent is 1 when an overdue reminder was created for that earlier installment.

SELECT l.account_no AS loan_account,
       l.external_id,
       o.name AS office,
       c.display_name AS client,
       c.id AS client_id,
       earlier.installment AS earlier_installment,
       earlier.duedate AS earlier_due_date,
       (COALESCE(earlier.principal_amount, 0) - COALESCE(earlier.principal_completed_derived, 0) - COALESCE(earlier.principal_writtenoff_derived, 0)
        + COALESCE(earlier.interest_amount, 0) - COALESCE(earlier.interest_completed_derived, 0) - COALESCE(earlier.interest_waived_derived, 0)
          - COALESCE(earlier.interest_writtenoff_derived, 0) - COALESCE(earlier.interest_cancelled_derived, 0)
        + COALESCE(earlier.fee_charges_amount, 0) - COALESCE(earlier.fee_charges_completed_derived, 0) - COALESCE(earlier.fee_charges_waived_derived, 0)
          - COALESCE(earlier.fee_charges_writtenoff_derived, 0)
        + COALESCE(earlier.penalty_charges_amount, 0) - COALESCE(earlier.penalty_charges_completed_derived, 0)
          - COALESCE(earlier.penalty_charges_waived_derived, 0) - COALESCE(earlier.penalty_charges_writtenoff_derived, 0)
       ) AS earlier_outstanding,
       later.installment AS later_installment,
       later.duedate AS later_due_date,
       COALESCE(later.total_paid_in_advance_derived, 0) AS later_in_advance,
       CASE WHEN reminder.id IS NULL THEN 0 ELSE 1 END AS arrears_sms_sent
  FROM m_loan l
  JOIN m_office o ON o.id = l.office_id
  LEFT JOIN m_client c ON c.id = l.client_id
  JOIN m_loan_repayment_schedule earlier ON earlier.loan_id = l.id
  JOIN m_loan_repayment_schedule later
    ON later.loan_id = l.id
   AND later.installment > earlier.installment
   AND COALESCE(later.total_paid_in_advance_derived, 0) > 0
  LEFT JOIN m_loan_overdue_reminder reminder
    ON reminder.loan_id = l.id
   AND reminder.loan_schedule_id = earlier.id
 WHERE l.loan_status_id = 300
   AND earlier.completed_derived = 0
HAVING earlier_outstanding > 0
 ORDER BY o.name, l.account_no, earlier.installment, later.installment;
