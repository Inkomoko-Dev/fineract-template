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
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.portfolio.loanaccount.data.CrbKenyaMetropolRequestData;
import org.apache.fineract.portfolio.loanaccount.data.MetropolAccountInfoData;
import org.apache.fineract.portfolio.loanaccount.data.MetropolCrbCreditInfoEnchancedData;
import org.apache.fineract.portfolio.loanaccount.domain.MetropolCrbAccountProductType;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MetropolCrbReadPlatformServiceImpl implements MetropolCrbReadPlatformService {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public CrbKenyaMetropolRequestData fetchIdentityVerificationDetails(Integer loanId) {
        try {
            final IdentityVerificationCreditMapper mapper = new IdentityVerificationCreditMapper();
            final String sql = "SELECT " + mapper.schema() + " order by idty.id DESC LIMIT 1 ";
            return this.jdbcTemplate.queryForObject(sql, mapper, new Object[] { loanId });
        } catch (final EmptyResultDataAccessException e) {
            return new CrbKenyaMetropolRequestData();
        }
    }

    @Override
    public MetropolCrbCreditInfoEnchancedData fetchCreditInfoEnhancedDetails(Integer loanId) {
        try {
            final MetropolCrbCreditInfoEnchancedMapper mapper = new MetropolCrbCreditInfoEnchancedMapper();
            final String sql = "SELECT " + mapper.schema() + " order by cie.id DESC LIMIT 1 ";
            return this.jdbcTemplate.queryForObject(sql, mapper, new Object[] { loanId });
        } catch (final EmptyResultDataAccessException e) {
            // Optional association on the identity-verification GET — no report yet is a valid state.
            return null;
        }
    }

    @Override
    public List<MetropolAccountInfoData> fetchAccountInfoDetails(Integer creditInfoEnhancedId) {
        final MetropolAccountInfoDataMapper mapper = new MetropolAccountInfoDataMapper();
        final String sql = "SELECT " + mapper.schema() + "order by info.id ASC ";
        return this.jdbcTemplate.query(sql, mapper, new Object[] { creditInfoEnhancedId });
    }

    private static final class IdentityVerificationCreditMapper implements RowMapper<CrbKenyaMetropolRequestData> {

        public String schema() {
            final StringBuilder sql = new StringBuilder();

            sql.append("    idty.client_id      AS clientId, " + "       idty.loan_id        AS loanId, "
                    + "       idty.id             AS id, " + "       idty.citizenship    AS citizenShip, "
                    + "       idty.clan           AS clan, " + "       idty.date_of_birth  AS dateOfBirth, "
                    + "       idty.date_of_death  AS dateOfDeath, " + "       idty.date_of_issue  As dateOfIssue, "
                    + "       idty.ethnic_group   AS ethnicGroup, " + "       idty.family         AS family, "
                    + "       idty.first_name     AS firstName, " + "       idty.gender         AS gender, "
                    + "       identity_number     AS identityNumber, " + "       CASE "
                    + "           WHEN idty.identity_type = '001' THEN 'National ID' "
                    + "           WHEN idty.identity_type = '002' THEN 'Passport' "
                    + "           WHEN idty.identity_type = '003' THEN 'Service ID' "
                    + "           WHEN idty.identity_type = '004' THEN 'Alien Registration' "
                    + "           WHEN idty.identity_type = '005' THEN 'Company/Business Registration' " + "           ELSE 'Unknown Type' "
                    + "           END             AS identityType, " + "       idty.last_name      AS lastName, "
                    + "       idty.occupation     AS occupation, " + "       idty.other_name     AS otherName, "
                    + "       idty.place_of_birth AS placeOfBirth, " + "       idty.place_of_death AS placeOfDeath, "
                    + "       idty.place_of_live  AS placeOfLive, " + "       idty.reg_office     AS refOffice, "
                    + "       idty.serial_number  AS serialNumber, " + "       idty.trx_id         AS trxId, "
                    + "       idty.created_on_utc As createdOn " + " FROM m_metropol_crb_identity_report idty "
                    + "         INNER JOIN m_client mc on idty.client_id = mc.id "
                    + "         INNER JOIN m_loan ml on idty.loan_id = ml.id " + "         WHERE idty.loan_id = ? ");
            return sql.toString();
        }

        @Override
        public CrbKenyaMetropolRequestData mapRow(final ResultSet rs, @SuppressWarnings("unused") final int rowNum) throws SQLException {
            final Integer id = rs.getInt("id");
            final Integer clientId = rs.getInt("clientId");
            final Integer loanId = rs.getInt("loanId");
            final String citizenShip = rs.getString("citizenShip");
            final String clan = rs.getString("clan");
            final String dateOfBirth = rs.getString("dateOfBirth");
            final String dateOfDeath = rs.getString("dateOfDeath");
            final String dateOfIssue = rs.getString("dateOfIssue");
            final String ethnicGroup = rs.getString("ethnicGroup");
            final String family = rs.getString("family");
            final String firstName = rs.getString("firstName");
            final String gender = rs.getString("gender");
            final String identityNumber = rs.getString("identityNumber");
            final String identityType = rs.getString("identityType");
            final String lastName = rs.getString("lastName");
            final String occupation = rs.getString("occupation");
            final String otherName = rs.getString("otherName");
            final String placeOfBirth = rs.getString("placeOfBirth");
            final String placeOfDeath = rs.getString("placeOfDeath");
            final String placeOfLive = rs.getString("placeOfLive");
            final String refOffice = rs.getString("refOffice");
            final String serialNumber = rs.getString("serialNumber");
            final String trxId = rs.getString("trxId");
            final String createdOn = rs.getString("createdOn");

            return new CrbKenyaMetropolRequestData(id, clientId, loanId, citizenShip, clan, dateOfBirth, dateOfDeath, dateOfIssue,
                    ethnicGroup, family, firstName, gender, identityNumber, identityType, lastName, occupation, otherName, placeOfBirth,
                    placeOfDeath, placeOfLive, refOffice, serialNumber, trxId, createdOn);

        }
    }

    private static final class MetropolCrbCreditInfoEnchancedMapper implements RowMapper<MetropolCrbCreditInfoEnchancedData> {

        public String schema() {
            final StringBuilder sql = new StringBuilder();

            sql.append("    cie.id                                    AS id, "
                    + "       cie.loan_id                               AS loanId,  "
                    + "       cie.client_id                             AS clientId,  "
                    + "       cie.report_type                           AS reportType,  "
                    + "       cie.api_code                              AS apiCode,  "
                    + "       cie.api_code_description                  AS apiCodeDescription,  "
                    + "       cie.application_ref_no                    AS applicationRefNo,  "
                    + "       cie.credit_score                          AS creditScore,  "
                    + "       cie.delinquency_code                      AS delinquencyCode,  "
                    + "       cie.has_error                             AS hasError,  "
                    + "       cie.has_fraud                             AS hasFraud,  "
                    + "       cie.identity_number                       AS identityNumber,  "
                    + "       cie.identity_type                         AS identityType,  "
                    + "       cie.is_guarantor                          AS hasGaurantor,  "
                    + "       cie.trx_id                                AS trxId,  " + "  "
                    + "       mmls.bank_account_npa                     AS lenderBankAccountNpa,  "
                    + "       mmls.bank_account_performing              AS lenderBankAccountPerforming,  "
                    + "       mmls.bank_account_performing_npa_history  AS lenderBankAccountPerformingNpaHistory,  "
                    + "       mmls.other_account_npa                    AS lenderOtherAccountNpa,  "
                    + "       mmls.other_account_performing             AS lenderOtherAccountPerforming,  "
                    + "       mmls.other_account_performing_npa_history AS lenderOtherAccountPerformingNpaHistory,  " + "  "
                    + "       mmnobc.last_12_months                     AS bChecquesLast12Months,  "
                    + "       mmnobc.last_3_months                      AS bChecquesLast3Months,  "
                    + "       mmnobc.last_6_months                      AS bChecquesLast6Months,  " + "  "
                    + "       mmnoca.last_12_months                     AS creditApLast12Months,  "
                    + "       mmnoca.last_3_months                      AS creditApLast3Months,  "
                    + "       mmnoca.last_6_months                      AS creditApLast6Months,  " + "  "
                    + "       mmnoe.last_12_months                      AS enquiriesApLast12Months,  "
                    + "       mmnoe.last_3_months                       AS enquiriesApLast3Months,  "
                    + "       mmnoe.last_6_months                       AS enquiriesApLast6Months , "
                    + "         ppi.month                                 AS ppiAnalysisMonth, "
                    + "         ppi.ppi                                   AS ppiAnalysisPpi, "
                    + "         ppi.ppi_rank                              AS ppiAnalysisPpiRank, "
                    + "         mmvn.first_name                           AS verifiedNameFirstName, "
                    + "         mmvn.other_name                           AS verifiedNameOthername, "
                    + "         mmvn.surname                              AS verifiedNameSurname "
                    + " FROM m_metropol_crb_credit_info_enhanced_report cie  "
                    + "         LEFT JOIN m_metropol_lender_sector mmls on cie.id = mmls.credit_info_enhanced_id  "
                    + "         LEFT JOIN m_metropol_no_of_bounced_cheques mmnobc on cie.id = mmnobc.credit_info_enhanced_id  "
                    + "         LEFT JOIN m_metropol_no_of_credit_applications mmnoca on cie.id = mmnoca.credit_info_enhanced_id  "
                    + "         LEFT JOIN m_metropol_number_of_enquiries mmnoe on cie.id = mmnoe.credit_info_enhanced_id  " + " "
                    + "           LEFT JOIN m_metropol_ppi_analysis ppi ON cie.id = ppi.credit_info_enhanced_id "
                    + "           LEFT JOIN m_metropol_verified_name mmvn on cie.id = mmvn.credit_info_enhanced_id "
                    + "WHERE loan_id = ? ");
            return sql.toString();
        }

        @Override
        public MetropolCrbCreditInfoEnchancedData mapRow(final ResultSet rs, @SuppressWarnings("unused") final int rowNum)
                throws SQLException {
            final Integer id = rs.getInt("id");
            final Integer clientId = rs.getInt("clientId");
            final Integer loanId = rs.getInt("loanId");
            final String reportType = rs.getString("reportType");
            final String apiCode = rs.getString("apiCode");
            final String apiCodeDescription = rs.getString("apiCodeDescription");
            final String applicationRefNo = rs.getString("applicationRefNo");
            final String creditScore = rs.getString("creditScore");
            final String delinquencyCode = rs.getString("delinquencyCode");
            final Boolean hasError = rs.getBoolean("hasError");
            final Boolean hasFraud = rs.getBoolean("hasFraud");
            final String identityNumber = rs.getString("identityNumber");
            final String identityType = rs.getString("identityType");
            final Boolean isGuarantor = rs.getBoolean("hasGaurantor");
            final String trxId = rs.getString("trxId");
            final Integer lenderBankAccountNpa = rs.getInt("lenderBankAccountNpa");
            final Integer lenderBankAccountPerforming = rs.getInt("lenderBankAccountPerforming");
            final Integer lenderBankAccountPerformingNpaHistory = rs.getInt("lenderBankAccountPerformingNpaHistory");
            final Integer lenderOtherAccountNpa = rs.getInt("lenderOtherAccountNpa");
            final Integer lenderOtherAccountPerforming = rs.getInt("lenderOtherAccountPerforming");
            final Integer lenderOtherAccountPerformingNpaHistory = rs.getInt("lenderOtherAccountPerformingNpaHistory");
            final Integer bChecquesLast12Months = rs.getInt("bChecquesLast12Months");
            final Integer bChecquesLast3Months = rs.getInt("bChecquesLast3Months");
            final Integer bChecquesLast6Months = rs.getInt("bChecquesLast6Months");
            final Integer creditApLast12Months = rs.getInt("creditApLast12Months");
            final Integer creditApLast3Months = rs.getInt("creditApLast3Months");
            final Integer creditApLast6Months = rs.getInt("creditApLast6Months");
            final Integer enquiriesApLast12Months = rs.getInt("enquiriesApLast12Months");
            final Integer enquiriesApLast3Months = rs.getInt("enquiriesApLast3Months");
            final Integer enquiriesApLast6Months = rs.getInt("enquiriesApLast6Months");

            final String ppiAnalysisMonth = rs.getString("ppiAnalysisMonth");
            final Double ppiAnalysisPpi = rs.getDouble("ppiAnalysisPpi");
            final String ppiAnalysisPpiRank = rs.getString("ppiAnalysisPpiRank");

            final String verifiedNameFirstName = rs.getString("verifiedNameFirstName");
            final String verifiedNameOthername = rs.getString("verifiedNameOthername");
            final String verifiedNameSurname = rs.getString("verifiedNameSurname");

            return new MetropolCrbCreditInfoEnchancedData(id, clientId, loanId, reportType, apiCode, apiCodeDescription, applicationRefNo,
                    creditScore, delinquencyCode, hasError, hasFraud, identityNumber, identityType, isGuarantor, trxId,
                    lenderBankAccountNpa, lenderBankAccountPerforming, lenderBankAccountPerformingNpaHistory, lenderOtherAccountNpa,
                    lenderOtherAccountPerforming, lenderOtherAccountPerformingNpaHistory, bChecquesLast12Months, bChecquesLast3Months,
                    bChecquesLast6Months, creditApLast12Months, creditApLast3Months, creditApLast6Months, enquiriesApLast12Months,
                    enquiriesApLast3Months, enquiriesApLast6Months, ppiAnalysisMonth, ppiAnalysisPpi, ppiAnalysisPpiRank,
                    verifiedNameFirstName, verifiedNameOthername, verifiedNameSurname);

        }
    }

    private static final class MetropolAccountInfoDataMapper implements RowMapper<MetropolAccountInfoData> {

        public String schema() {
            final StringBuilder sql = new StringBuilder();

            sql.append("   info.id                      AS id,   " + "       info.account_number          AS accountNumber,   "
                    + "       info.account_status          AS accountStatus,   "
                    + "       info.current_balance         AS currentBalance,   " + "       info.date_opened             AS dateOpened,   "
                    + "       info.days_in_arrears         AS daysInArrears,   "
                    + "       info.delinquency_code        AS delinquencyCode,   "
                    + "       info.highest_days_in_arrears AS highestDaysInArrears,   "
                    + "       info.is_your_account         AS isYourAccount,   "
                    + "       info.last_payment_amount     AS lastPaymentAmount,   "
                    + "       info.last_payment_date       AS lastPaymentDate,   " + "       info.loaded_at               AS loadedAt,   "
                    + "       info.original_amount         AS originalAmount,   "
                    + "       info.overdue_balance         AS overdueBalance,   " + "       info.overdue_date            AS overdueDate,   "
                    + "       info.product_type_id         AS productTypeId   " + " FROM m_metropol_account_info info   "
                    + " WHERE info.credit_info_enhanced_id = ?  ");
            return sql.toString();
        }

        @Override
        public MetropolAccountInfoData mapRow(final ResultSet rs, @SuppressWarnings("unused") final int rowNum) throws SQLException {

            final Integer id = rs.getInt("id");
            final String accountNumber = rs.getString("accountNumber");
            final String accountStatus = rs.getString("accountStatus");
            final String currentBalance = rs.getString("currentBalance");
            final String dateOpened = rs.getString("dateOpened");
            final Integer daysInArrears = rs.getInt("daysInArrears");
            final String delinquencyCode = rs.getString("delinquencyCode");
            final Integer highestDaysInArrears = rs.getInt("highestDaysInArrears");
            final Boolean isYourAccount = rs.getBoolean("isYourAccount");
            final String lastPaymentAmount = rs.getString("lastPaymentAmount");
            final String lastPaymentDate = rs.getString("lastPaymentDate");
            final String loadedAt = rs.getString("loadedAt");
            final String originalAmount = rs.getString("originalAmount");
            final String overdueBalance = rs.getString("overdueBalance");
            final String overdueDate = rs.getString("overdueDate");
            final Integer productTypeId = rs.getInt("productTypeId");
            final MetropolCrbAccountProductType accountType = MetropolCrbAccountProductType.fromCode(productTypeId);

            return new MetropolAccountInfoData(id, accountNumber, accountStatus, currentBalance, dateOpened, daysInArrears, delinquencyCode,
                    highestDaysInArrears, isYourAccount, lastPaymentAmount, lastPaymentDate, loadedAt, originalAmount, overdueBalance,
                    overdueDate, productTypeId, accountType);

        }
    }
}
