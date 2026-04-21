/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.fineract.portfolio.loanaccount.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Collection;

import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.domain.JdbcSupport;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.core.service.database.DatabaseTypeResolver;
import org.apache.fineract.portfolio.loanaccount.data.TransUnionRwandaCorporateCreditData;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TransUnionCrbPostCorporateCreditReadPlatformServiceImpl implements TransUnionCrbPostCorporateCreditReadPlatformService {

    private final JdbcTemplate jdbcTemplate;
    private final DatabaseTypeResolver databaseTypeResolver;

    @Override
    public Collection<TransUnionRwandaCorporateCreditData> retrieveAllCorporateCredits() {
        final CorporateCreditMapper mapper = new CorporateCreditMapper();
        final String sql = mapper.schema() + " order by l.id ";
        return this.jdbcTemplate.query(sql, mapper, new Object[] {});
    }

    @Override
    public Collection<TransUnionRwandaCorporateCreditData> retrieveAllCorporateCreditsPage(long lastLoanId, int pageSize) {
        final CorporateCreditMapper mapper = new CorporateCreditMapper();

        final String sql = mapper.schema() + " AND l.id > ? order by l.id limit ?";

        return this.jdbcTemplate.query(
                sql,
                mapper,
                lastLoanId,
                pageSize
        );
    }

    private final class CorporateCreditMapper implements RowMapper<TransUnionRwandaCorporateCreditData> {

        public String schema() {
            final StringBuilder sql = new StringBuilder();
            final String daysInArrearsExpression = daysInArrearsExpression();
            final String addressTypePriorityExpression = "CASE "
                    + "WHEN UPPER(address_type_cv.code_value) IN ('CURRENT ADDRESS', 'PRIMARY', 'PRIMARY ADDRESS') THEN 0 "
                    + "WHEN UPPER(address_type_cv.code_value) IN ('HOME', 'RESIDENTIAL', 'RESIDENTIAL ADDRESS') THEN 1 "
                    + "WHEN UPPER(address_type_cv.code_value) = 'BUSINESS' THEN 2 "
                    + "ELSE 3 END";

            sql.append(" WITH RankedAddresses AS ( " + "    SELECT ca.client_id, " + "           ca.address_id, "
                    + "           address_type_cv.code_value AS addressType, "
                    + "           ROW_NUMBER() OVER (PARTITION BY ca.client_id "
                    + "                              ORDER BY CASE WHEN ca.is_active = true THEN 0 ELSE 1 END, "
                    + "                                       " + addressTypePriorityExpression + ", "
                    + "                                       ca.address_id DESC) AS row_num "
                    + "    FROM m_client_address ca "
                    + "         LEFT JOIN m_code_value address_type_cv ON ca.address_type_id = address_type_cv.id " + " ) "
                    + " SELECT l.id                                                                              AS loanId, "
                    + "       l.account_no                                                                      AS accountNumber, "
                    + "       l.loan_status_id                                                                  AS loanStatus, "
                    + "       l.currency_code                                                                   AS currencyType, "
                    + "       ranked_address.address_id                                                         AS selectedAddressId, "
                    + "       ranked_address.addressType                                                        AS selectedAddressType, "
                    + "       country_cv.code_value                                                             AS country, "
                    + "       mc.fullname                                                                      AS institution, "
                    + "       mc.fullname                                                                      AS tradingName, "
                    + "       ")
                    .append(daysInArrearsExpression)
                    .append("                                                                           AS daysInArrears, ");
            sql.append("  l.principal_amount                                                    AS openingBalance, " + "       CASE "
                    + "           WHEN l.repayment_period_frequency_enum = 0 THEN 'DLY' "
                    + "           WHEN l.repayment_period_frequency_enum = 1 THEN 'WKY' "
                    + "           WHEN l.repayment_period_frequency_enum = 2 THEN 'MTH' "
                    + "           WHEN l.repayment_period_frequency_enum = 3 THEN 'ANN' " + "           ELSE 'IRR' "
                    + "           END                                                                           AS accountRepaymentTerm, "
                    + "       l.total_outstanding_derived                                                       AS currentBalance, "
                    + "       IF(l.loan_type_enum = 1, 'O', 'G') AS accountOwner, " + "       CASE "
                    + "           WHEN l.repayment_period_frequency_enum = 1 THEN 'W' "
                    + "           WHEN l.repayment_period_frequency_enum = 2 THEN 'M' "
                    + "           WHEN l.repayment_period_frequency_enum = 3 THEN 'A' "
                    + "           END                                                                           AS incomeFrequency, "
                    + "       nextPaymentTbl.scheduledPaymentAmount                       AS scheduledPaymentAmount, "
                    + "       mc.mobile_no                                                                      AS telephone1, "
                    + "       (l.principal_repaid_derived + l.interest_repaid_derived)                          AS actualPaymentAmount, "
                    + "       l.disbursedon_date                                                                AS dateAccountOpened, "
                    + "       l.nominal_interest_rate_per_period                                                AS interestRateAtDisbursement, "
                    + "       nationality_cv.code_value                                                         AS nationality, "
                    + "       ra.postal_code                                                                    AS postalCode, "
                    + "       province_cv.code_value                                                            AS physicalAddressProvince, "
                    + "       ra.postal_code                                                                    AS postalAddressNumber, "
                    + "       l.approvedon_date                                                                 AS approvalDate, "
                    + "       first_payment.firstPaymentDate                                                    AS firstPaymentDate, "
                    + "       l.closedon_date                                                                   AS dateClosed, "
                    + "       CASE " + "           WHEN l.loan_status_id = 300 THEN 'A' "
                    + "           WHEN l.loan_status_id = 600 THEN 'C' " + "           WHEN l.loan_status_id = 601 THEN 'W' "
                    + "           WHEN l.loan_status_id = 700 THEN 'X' "
                    + "           END                                                                           AS accountStatus, "
                    + "       l.number_of_repayments                                                            AS termsDuration, "
                    + "       l.last_repayment_date                                                             AS lastPaymentDate, "
                    + "       mc.date_of_birth                                                                  AS companyRegistrationDate, "
                    + "       l.expected_maturedon_date                                                                  AS finalPaymentDate, "
                    + "       mlaa.principal_overdue_derived                                                    AS amountPastDue, "
                    + "       40                                                                                AS category, "
                    + "       business_line_cv.external_code                                                    AS sectorOfActivity, "
                    + "       'I'                                                                               AS accountType, "
                    + "       ra.physical_address_district                                                      AS physicalAddressDistrict, "
                    + "       ''                                                                                AS groupName, "
                    + "       ")
                    .append(currentBalanceIndicatorExpression(daysInArrearsExpression))
                    .append("        AS currentBalanceIndicator, ");
            sql.append("       ra.physical_address_sector                                                        AS physicalAddressSector, "
                    + "       0                                                                                 AS numberOfJointLoanParticipants, "
                    + "       ra.physical_address_cell                                                          AS physicalAddressCell, "
                    + "       ra.address_line_1                                                                 AS physicalAddressLine1, "
                    + "       13                                                                                AS nature, "
                    + "       ")
                    .append(classificationExpression(daysInArrearsExpression))
                    .append("                                                                                            AS classification, ");

            sql.append("      ''                                                                                AS emailAddress, "
                    + "       'T'                                                                               AS residenceType, "
                    + "        l.total_outstanding_derived                           AS availableCredit, "
                    + "       0                                                                                 AS income, "
                    + "      now()                                                             AS dateAccountUpdated, "
                    + "       r.installments_in_arrears                                                         AS installmentsInArrears, "
                    + "       mcnp.incorp_no                                                                    AS companyRegNo, "
                    + "       business_line_cv.external_code                                                       AS industry, "
                    + "       other_info.tax_identification_number                                              AS taxNo "
                    + " FROM m_loan l " + "         INNER JOIN m_product_loan mpl ON l.product_id = mpl.id "
                    + "         INNER JOIN m_client mc ON l.client_id = mc.id "
                    + "         INNER JOIN m_client_non_person mcnp on mc.id = mcnp.client_id "
                    + "         LEFT JOIN m_loan_arrears_aging mlaa ON l.id = mlaa.loan_id "
                    + "         LEFT JOIN m_client_other_info info ON mc.id = info.client_id "
                    + "         LEFT JOIN m_code_value nationality_cv ON info.nationality_cv_id = nationality_cv.id "
                    + "         LEFT JOIN m_code_value business_line_cv ON mcnp.main_business_line_cv_id = business_line_cv.id "
                    + "         LEFT JOIN m_client_additional_info ad_info ON mc.id = ad_info.client_id "
                    + "         LEFT JOIN m_client_other_info other_info ON mc.id = other_info.client_id " + "         LEFT JOIN (\n" +
                    "    SELECT\n" +
                    "        x.loan_id,\n" +
                    "        COALESCE(x.first_txn_date, x.first_sched_due_date) AS firstPaymentDate\n" +
                    "    FROM (\n" +
                    "        SELECT\n" +
                    "            l.id AS loan_id,\n" +
                    "            (\n" +
                    "                SELECT MIN(t.transaction_date)\n" +
                    "                FROM m_loan_transaction t\n" +
                    "                WHERE t.loan_id = l.id\n" +
                    "                  AND t.transaction_type_enum = 2\n" +
                    "            ) AS first_txn_date,\n" +
                    "            (\n" +
                    "                SELECT MIN(rs.duedate)\n" +
                    "                FROM m_loan_repayment_schedule rs\n" +
                    "                WHERE rs.loan_id = l.id\n" +
                    "                  AND (IFNULL(rs.principal_amount, 0)\n" +
                    "                     + IFNULL(rs.interest_amount, 0)\n" +
                    "                     + IFNULL(rs.fee_charges_amount, 0)\n" +
                    "                     + IFNULL(rs.penalty_charges_amount, 0)) > 0\n" +
                    "            ) AS first_sched_due_date\n" +
                    "        FROM m_loan l\n" +
                    "    ) x\n" +
                    ") AS first_payment\n" +
                    "  ON l.id = first_payment.loan_id "
                    + "         LEFT JOIN RankedAddresses ranked_address ON mc.id = ranked_address.client_id "
                    + "                                                   AND ranked_address.row_num = 1 "
                    + "         LEFT JOIN m_address ra ON ranked_address.address_id = ra.id "
                    + "         LEFT JOIN m_code_value country_cv ON ra.country_id = country_cv.id "
                    + "         LEFT JOIN (SELECT loan_id, " + "                           COUNT(*) AS installments_in_arrears "
                    + "                    FROM m_loan_repayment_schedule " + "                    WHERE duedate <= CURRENT_DATE "
                    + "                      AND completed_derived = FALSE " + "                      AND obligations_met_on_date IS NULL "
                    + "                    GROUP BY loan_id) AS r ON l.id = r.loan_id "
                    + "         LEFT JOIN m_code_value province_cv ON ra.state_province_id = province_cv.id  "
                    + "             LEFT JOIN (   SELECT lrs.duedate                                                      AS nextPaymentDueDate, "
                    + "                                                    lrs.loan_id, "
                    + "                                                    IFNULL(lrs.principal_amount, 0)                                  AS scheduledPrincipalAmount, "
                    + "                                                    IFNULL(lrs.interest_amount, 0)                                   AS scheduledInterestAmount, "
                    + "                                                    IFNULL(lrs.fee_charges_amount, 0)                                AS scheduledFeesAmount, "
                    + "                                                    IFNULL(lrs.principal_amount, 0) + IFNULL(lrs.interest_amount, 0) AS scheduledPaymentAmount"
                    + "                                             FROM (  SELECT lrs.*, "
                    + "                                                                          ROW_NUMBER() OVER (PARTITION BY lrs.loan_id ORDER BY lrs.installment ASC) AS row_num "
                    + "                                                                   FROM m_loan_repayment_schedule lrs "
                    + "                                                                   WHERE lrs.completed_derived = false AND lrs.obligations_met_on_date IS NULL "
                    + "                                                                   GROUP BY lrs.loan_id,lrs.installment,lrs.id ORDER BY lrs.installment ASC "
                    + "                                                  ) lrs    WHERE lrs.row_num = 1 "
                    + "                                         ) AS nextPaymentTbl on nextPaymentTbl.loan_id = l.id"
                    + " WHERE l.loan_status_id IN (300, 600, 601, 700) " + "  AND l.currency_code = 'RWF' "
                    + "  AND mc.legal_form_enum = 2  "
                    + "  AND (l.stop_consumer_credit_upload_to_trans_union IS NULL OR l.stop_consumer_credit_upload_to_trans_union = false) ");
            return sql.toString();
        }

        private String daysInArrearsExpression() {
            if (databaseTypeResolver.isMySQL()) {
                return "COALESCE(DATEDIFF(NOW(), mlaa.overdue_since_date_derived), 0)";
            }
            return "COALESCE(CAST(EXTRACT(DAY FROM (now()::TIMESTAMP - mlaa.overdue_since_date_derived::TIMESTAMP)) AS INTEGER), 0)";
        }

        private String currentBalanceIndicatorExpression(String daysInArrearsExpression) {
            return "CASE "
                    + "    WHEN l.loan_status_id IN(600,601,700) THEN 'C' "
                    + "    WHEN " + daysInArrearsExpression + " > 90 THEN 'D' "
                    + "    ELSE 'C' "
                    + "    END";
        }

        private String classificationExpression(String daysInArrearsExpression) {
            return "CASE "
                    + "           WHEN " + daysInArrearsExpression + " < 30 THEN 1 "
                    + "           WHEN " + daysInArrearsExpression + " BETWEEN 31 AND 90 THEN 2 "
                    + "           WHEN " + daysInArrearsExpression + " BETWEEN 91 AND 180 THEN 3 "
                    + "           WHEN " + daysInArrearsExpression + " BETWEEN 181 AND 365 THEN 4 "
                    + "           WHEN " + daysInArrearsExpression + " BETWEEN 366 AND 719 THEN 5 "
                    + "           WHEN " + daysInArrearsExpression + " > 720 THEN 6 "
                    + "          END";
        }

        @Override
        public TransUnionRwandaCorporateCreditData mapRow(final ResultSet rs, @SuppressWarnings("unused") final int rowNum)
                throws SQLException {
            final Integer loanId = rs.getInt("loanId");
            final String accountNumber = rs.getString("accountNumber");
            final Integer loanStatus = rs.getInt("loanStatus");
            final String currencyType = rs.getString("currencyType");
            final Number selectedAddressIdValue = (Number) rs.getObject("selectedAddressId");
            final Long selectedAddressId = selectedAddressIdValue != null ? selectedAddressIdValue.longValue() : null;
            final String selectedAddressType = rs.getString("selectedAddressType");
            final String institution = rs.getString("institution");
            final String tradingName = rs.getString("tradingName");

            final String phoneNumber = rs.getString("telephone1");
            String telephone1 = (phoneNumber != null) ? phoneNumber.replace(" ", "") : null; // Remove Spaces from
            // phone Number
            // because CRB
            // TransUnion does not
            // accept spaces in
            // phone number.
            // FSI-173

            final String companyRegNo = rs.getString("companyRegNo");
            final String physicalAddressLine1 = rs.getString("physicalAddressLine1");
            final String industry = rs.getString("industry");
            final String taxNo = rs.getString("taxNo");
            final String country = rs.getString("country");
            final Integer daysInArrears = rs.getInt("daysInArrears");
            final Integer openingBalance = rs.getInt("openingBalance");
            final String accountRepaymentTerm = rs.getString("accountRepaymentTerm");
            final Integer currentBalance = rs.getInt("currentBalance");
            final String accountOwner = rs.getString("accountOwner");
            final String incomeFrequency = rs.getString("incomeFrequency");
            final Integer scheduledPaymentAmount = rs.getInt("scheduledPaymentAmount");
            final Integer actualPaymentAmount = rs.getInt("actualPaymentAmount");
            final LocalDate dateAccountOpened = JdbcSupport.getLocalDate(rs, "dateAccountOpened");
            final LocalDate companyRegistrationDate = JdbcSupport.getLocalDate(rs, "companyRegistrationDate");
            final Double interestRateAtDisbursement = rs.getDouble("interestRateAtDisbursement");
            final String nationality = rs.getString("nationality");
            final String postalCode = rs.getString("postalCode");
            final String physicalAddressProvince = rs.getString("physicalAddressProvince");
            final String postalAddressNumber = rs.getString("postalAddressNumber");
            final LocalDate approvalDate = JdbcSupport.getLocalDate(rs, "approvalDate");
            final LocalDate firstPaymentDate = JdbcSupport.getLocalDate(rs, "firstPaymentDate");
            final LocalDate dateClosed = JdbcSupport.getLocalDate(rs, "dateClosed");
            final String accountStatus = rs.getString("accountStatus");
            final Integer termsDuration = rs.getInt("termsDuration");
            final LocalDate lastPaymentDate = JdbcSupport.getLocalDate(rs, "lastPaymentDate");
            final LocalDate finalPaymentDate = JdbcSupport.getLocalDate(rs, "finalPaymentDate");
            final Integer amountPastDue = rs.getInt("amountPastDue");
            final Integer category = rs.getInt("category");
            final String sectorOfActivity = rs.getString("sectorOfActivity");
            final String accountType = rs.getString("accountType");
            final String physicalAddressDistrict = rs.getString("physicalAddressDistrict");
            final String groupName = rs.getString("groupName");
            final String currentBalanceIndicator = rs.getString("currentBalanceIndicator");
            final String physicalAddressSector = rs.getString("physicalAddressSector");
            final Integer numberOfJointLoanParticipants = rs.getInt("numberOfJointLoanParticipants");
            final String physicalAddressCell = rs.getString("physicalAddressCell");
            final Integer nature = rs.getInt("nature");
            final Integer installmentsInArrears = rs.getInt("installmentsInArrears");
            final Integer classification = rs.getInt("classification");
            final String emailAddress = rs.getString("emailAddress");
            final String residenceType = rs.getString("residenceType");
            final Integer availableCredit = rs.getInt("availableCredit");
            final Integer income = rs.getInt("income");
            final LocalDate dateAccountUpdated = JdbcSupport.getLocalDate(rs, "dateAccountUpdated");

            TransUnionRwandaCorporateCreditData trans = new TransUnionRwandaCorporateCreditData();
            trans.setSelectedAddressId(selectedAddressId);
            trans.setSelectedAddressType(selectedAddressType);
            trans.setLoanId(loanId);
            trans.setLoanStatus(loanStatus);
            trans.setInstitution(institution);
            trans.setTradingName(tradingName);
            trans.setTelephone1(telephone1);
            trans.setCompanyRegNo(companyRegNo);
            trans.setIndustry(industry);
            trans.setTaxNo(taxNo);
            trans.setCurrencyType(currencyType);
            trans.setPhysicalAddressLine1(physicalAddressLine1);
            trans.setCountry(country);
            trans.setDaysInArrears(daysInArrears);
            trans.setOpeningBalance(openingBalance);
            trans.setAccountRepaymentTerm(accountRepaymentTerm);
            trans.setCurrentBalance(currentBalance);
            trans.setAccountOwner(accountOwner);
            trans.setIncomeFrequency(incomeFrequency);
            trans.setScheduledPaymentAmount(scheduledPaymentAmount);
            trans.setActualPaymentAmount(actualPaymentAmount);
            trans.setDateAccountOpened(DateUtils.convertLocalDateToLong(dateAccountOpened));
            trans.setCompanyRegistrationDate(DateUtils.convertLocalDateToLong(companyRegistrationDate));
            trans.setInterestRateAtDisbursement(interestRateAtDisbursement);
            trans.setNationality(nationality);
            trans.setPostalCode(postalCode);
            trans.setPhysicalAddressProvince(physicalAddressProvince);
            trans.setPostalAddressNumber(postalAddressNumber);
            trans.setApprovalDate(DateUtils.convertLocalDateToLong(approvalDate));
            trans.setFirstPaymentDate(DateUtils.convertLocalDateToLong(firstPaymentDate));
            trans.setDateClosed(DateUtils.convertLocalDateToLong(dateClosed));
            trans.setAccountStatus(accountStatus);
            trans.setTermsDuration(termsDuration);
            trans.setLastPaymentDate(DateUtils.convertLocalDateToLong(lastPaymentDate));
            trans.setFinalPaymentDate(DateUtils.convertLocalDateToLong(finalPaymentDate));
            trans.setAmountPastDue(amountPastDue);
            trans.setCategory(category);
            trans.setSectorOfActivity(sectorOfActivity);
            trans.setAccountType(accountType);
            trans.setPhysicalAddressDistrict(physicalAddressDistrict);
            trans.setGroupName(groupName);
            trans.setCurrentBalanceIndicator(currentBalanceIndicator);
            trans.setPhysicalAddressSector(physicalAddressSector);
            trans.setNumberOfJointLoanParticipants(numberOfJointLoanParticipants);
            trans.setPhysicalAddressCell(physicalAddressCell);
            trans.setNature(nature);
            trans.setInstallmentsInArrears(installmentsInArrears);
            trans.setAccountNumber(accountNumber);
            trans.setEmailAddress(emailAddress);
            trans.setClassification(classification);
            trans.setResidenceType(residenceType);
            trans.setAvailableCredit(availableCredit);
            trans.setIncome(income);
            trans.setDateAccountUpdated(DateUtils.convertLocalDateToLong(dateAccountUpdated));

            return trans;

        }
    }

}
