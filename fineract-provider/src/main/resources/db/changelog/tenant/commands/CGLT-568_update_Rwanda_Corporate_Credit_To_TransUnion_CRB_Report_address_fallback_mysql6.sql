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

-- liquibase formatted sql
-- changeset fineract:1
-- MySQL dump 10.13  Distrib 5.1.60, for Win32 (ia32)
--
-- Host: localhost    Database: fineract_default
-- ------------------------------------------------------
-- Server version	5.1.60-community

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES UTF8MB4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

UPDATE stretchy_report SET report_sql = "WITH RankedAddresses AS (SELECT ca.client_id,
                                                                         ca.address_id,
                                                                         address_type_cv.code_value AS addressType,
                                                                         ROW_NUMBER() OVER (PARTITION BY ca.client_id
                                                                                            ORDER BY CASE WHEN ca.is_active = TRUE THEN 0 ELSE 1 END,
                                                                                                     CASE
                                                                                                         WHEN UPPER(address_type_cv.code_value) IN ('CURRENT ADDRESS', 'PRIMARY', 'PRIMARY ADDRESS') THEN 0
                                                                                                         WHEN UPPER(address_type_cv.code_value) IN ('HOME', 'RESIDENTIAL', 'RESIDENTIAL ADDRESS') THEN 1
                                                                                                         WHEN UPPER(address_type_cv.code_value) = 'BUSINESS' THEN 2
                                                                                                         ELSE 3 END,
                                                                                                     ca.address_id DESC) AS row_num
                                                                  FROM m_client_address ca
                                                                           LEFT JOIN m_code_value address_type_cv ON ca.address_type_id = address_type_cv.id)
                                         SELECT l.id                                                                       AS loanId,
                                                l.account_no                                                               AS accountNumber,
                                                l.loan_status_id                                                           AS loanStatus,
                                                l.currency_code                                                            AS currencyType,
                                                country_cv.code_value                                                      AS country,
                                                mc.fullname                                                                AS institution,
                                                mc.fullname                                                                AS tradingName,
                                                COALESCE(DATEDIFF(NOW(), mlaa.overdue_since_date_derived)  ,0)                            AS daysInArrears,
                                                l.principal_amount                                                   AS openingBalance,
                                                CASE
                                                    WHEN l.repayment_period_frequency_enum = 0 THEN 'DLY'
                                                    WHEN l.repayment_period_frequency_enum = 1 THEN 'WKY'
                                                    WHEN l.repayment_period_frequency_enum = 2 THEN 'MTH'
                                                    WHEN l.repayment_period_frequency_enum = 3 THEN 'ANN'
                                                    ELSE 'IRR' END                                                         AS accountRepaymentTerm,
                                                l.total_outstanding_derived                                                AS currentBalance,
                                                IF(l.loan_type_enum = 1, 'O', 'G')                                         AS accountOwner,
                                                CASE
                                                    WHEN l.repayment_period_frequency_enum = 1 THEN 'W'
                                                    WHEN l.repayment_period_frequency_enum = 2 THEN 'M'
                                                    WHEN l.repayment_period_frequency_enum = 3 THEN 'A' END                AS incomeFrequency,
                                               nextPaymentTbl.scheduledPaymentAmount                AS scheduledPaymentAmount,
                                                mc.mobile_no                                                               AS telephone1,
                                                (l.principal_repaid_derived + l.interest_repaid_derived)                   AS actualPaymentAmount,
                                                l.disbursedon_date                                                         AS dateAccountOpened,
                                                l.nominal_interest_rate_per_period                                         AS interestRateAtDisbursement,
                                                nationality_cv.code_value                                                  AS nationality,
                                                ra.postal_code                                                             AS postalCode,
                                                province_cv.code_value                                                     AS physicalAddressProvince,
                                                ra.postal_code                                                             AS postalAddressNumber,
                                                l.approvedon_date                                                          AS approvalDate,
                                                first_payment.firstPaymentDate                                             AS firstPaymentDate,
                                                l.closedon_date                                                            AS dateClosed,
                                                CASE
                                                    WHEN l.loan_status_id = 300 THEN 'A'
                                                    WHEN l.loan_status_id = 600 THEN 'C'
                                                    WHEN l.loan_status_id = 601 THEN 'W'
                                                    WHEN l.loan_status_id = 700 THEN 'X' END                               AS accountStatus,
                                                l.number_of_repayments                                                     AS termsDuration,
                                                l.last_repayment_date                                                      AS lastPaymentDate,
                                                mc.date_of_birth                                                           AS companyRegistrationDate,
                                                l.maturedon_date                                                           AS finalPaymentDate,
                                                mlaa.principal_overdue_derived                                             AS amountPastDue,
                                                40                                                                         AS category,
                                                'Other personal service activities n.e.c.'                                 AS sectorOfActivity,
                                                'I'                                                                        AS accountType,
                                                ra.physical_address_district                                               AS physicalAddressDistrict,
                                                ''                                                                         AS groupName,
                                                CASE
                                                    WHEN DATEDIFF(NOW(), mlaa.overdue_since_date_derived) <= 90   THEN 'C'
                                                    WHEN l.loan_status_id IN(600,601,700) THEN 'C'
                                                    ELSE 'D'
                                                    END                                                                    AS currentBalanceIndicator,
                                                ra.physical_address_sector                                                 AS physicalAddressSector,
                                                0                                                                          AS numberOfJointLoanParticipants,
                                                ra.physical_address_cell                                                   AS physicalAddressCell,
                                                ra.address_line_1                                                          AS physicalAddressLine1,
                                                13                                                                         AS nature,
                                                CASE
                                                    WHEN mlaa.overdue_since_date_derived IS NULL OR DATEDIFF(NOW(), mlaa.overdue_since_date_derived) < 30 THEN 1
                                                    WHEN DATEDIFF(NOW(), mlaa.overdue_since_date_derived) BETWEEN 31 AND 90 THEN 2
                                                    WHEN DATEDIFF(NOW(), mlaa.overdue_since_date_derived) BETWEEN 91 AND 180 THEN 3
                                                    WHEN DATEDIFF(NOW(), mlaa.overdue_since_date_derived) BETWEEN 181 AND 365 THEN 4
                                                    WHEN DATEDIFF(NOW(), mlaa.overdue_since_date_derived) BETWEEN 366 AND 719 THEN 5
                                                    WHEN DATEDIFF(NOW(), mlaa.overdue_since_date_derived) > 720 THEN 6 END AS classification,
                                                ''                                                                         AS emailAddress,
                                                'T'                                                                        AS residenceType,
                                                 l.total_outstanding_derived                     AS availableCredit,
                                                0                                                                          AS income,
                                               now()                                                      AS dateAccountUpdated,
                                                r.installments_in_arrears                                                  AS installmentsInArrears,
                                                mcnp.incorp_no                                                             AS companyRegNo,
                                                business_line_cv.code_value                                                AS industry,
                                                other_info.tax_identification_number                                       AS taxNo
                                         FROM m_loan l
                                                  INNER JOIN m_product_loan mpl ON l.product_id = mpl.id
                                                  INNER JOIN m_client mc ON l.client_id = mc.id
                                                  INNER JOIN m_client_non_person mcnp on mc.id = mcnp.client_id
                                                  LEFT JOIN m_loan_arrears_aging mlaa ON l.id = mlaa.loan_id
                                                  LEFT JOIN m_client_other_info info ON mc.id = info.client_id
                                                  LEFT JOIN m_code_value nationality_cv ON info.nationality_cv_id = nationality_cv.id
                                                  LEFT JOIN m_code_value business_line_cv ON mcnp.main_business_line_cv_id = business_line_cv.id
                                                  LEFT JOIN m_client_additional_info ad_info ON mc.id = ad_info.client_id
                                                  LEFT JOIN m_client_other_info other_info ON mc.id = other_info.client_id
                                                  LEFT JOIN (SELECT loan_id, transaction_date AS firstPaymentDate
                                                             FROM (SELECT loan_id,
                                                                          transaction_date,
                                                                          ROW_NUMBER() OVER (PARTITION BY loan_id ORDER BY transaction_date) AS row_num
                                                                   FROM m_loan_transaction
                                                                   WHERE transaction_type_enum = 2) ranked_transactions
                                                             WHERE row_num = 1) AS first_payment ON l.id = first_payment.loan_id
                                                  LEFT JOIN RankedAddresses ranked_address ON mc.id = ranked_address.client_id
                                                      AND ranked_address.row_num = 1
                                                  LEFT JOIN m_address ra ON ranked_address.address_id = ra.id
                                                  LEFT JOIN m_code_value country_cv ON ra.country_id = country_cv.id
                                                  LEFT JOIN (SELECT loan_id, COUNT(*) AS installments_in_arrears
                                                             FROM m_loan_repayment_schedule
                                                             WHERE duedate <= CURRENT_DATE
                                                               AND completed_derived = FALSE
                                                               AND obligations_met_on_date IS NULL
                                                             GROUP BY loan_id) AS r ON l.id = r.loan_id
                                                  LEFT JOIN m_code_value province_cv ON ra.state_province_id = province_cv.id
                                                  LEFT JOIN (
                                                                                               SELECT lrs.duedate                                                      AS nextPaymentDueDate,
                                                                                                      lrs.loan_id,
                                                                                                      IFNULL(lrs.principal_amount, 0)                                  AS scheduledPrincipalAmount,
                                                                                                      IFNULL(lrs.interest_amount, 0)                                   AS scheduledInterestAmount,
                                                                                                      IFNULL(lrs.fee_charges_amount, 0)                                AS scheduledFeesAmount,
                                                                                                      IFNULL(lrs.principal_amount, 0) + IFNULL(lrs.interest_amount, 0) AS scheduledPaymentAmount
                                                                                               FROM (
                                                                                                        SELECT lrs.*,
                                                                                                                            ROW_NUMBER() OVER (PARTITION BY lrs.loan_id ORDER BY lrs.installment ASC) AS row_num
                                                                                                                     FROM m_loan_repayment_schedule lrs
                                                                                                                     WHERE lrs.completed_derived = false AND lrs.obligations_met_on_date IS NULL
                                                                                                                     GROUP BY lrs.loan_id,lrs.installment,lrs.id ORDER BY lrs.installment ASC
                                                                                                    ) lrs

                                                                                               WHERE lrs.row_num = 1
                                                                                           ) AS nextPaymentTbl on nextPaymentTbl.loan_id = l.id
                                         WHERE l.loan_status_id IN (300, 600, 601, 700)
                                           AND l.currency_code = 'RWF'
                                           AND mc.legal_form_enum = 2
                                           AND first_payment.firstPaymentDate IS NOT NULL
                                           AND l.last_repayment_date IS NOT NULL
                                           AND (l.stop_consumer_credit_upload_to_trans_union IS NULL OR l.stop_consumer_credit_upload_to_trans_union = false)
                                         order by l.id " WHERE id = (SELECT tbl.id FROM (SELECT sr.id FROM stretchy_report sr WHERE sr.report_name = "Rwanda Corporate Credit To TransUnion (CRB) Report") AS tbl);
