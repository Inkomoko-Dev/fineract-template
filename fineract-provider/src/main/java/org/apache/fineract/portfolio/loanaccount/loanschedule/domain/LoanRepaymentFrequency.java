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
package org.apache.fineract.portfolio.loanaccount.loanschedule.domain;

import java.util.List;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.EnumOptionData;
import org.apache.fineract.portfolio.common.domain.PeriodFrequencyType;

/**
 * Named monthly repayment intervals reuse the existing monthly schedule engine
 * ({@code repaymentEvery} + {@link PeriodFrequencyType#MONTHS}): 1 = monthly, 3 = quarterly, 6 = semi-annual.
 * <p>
 * Interest, principal, fees and penalties continue to use the product's existing interest method (flat, declining
 * balance, etc.). The repayment interval only changes the length of each schedule period: quarterly and semi-annual
 * installments accrue over 3- and 6-month periods respectively, with the same first-due-date, grace, and last-installment
 * rounding rules as monthly loans. Reports and collections views must use installment due dates and
 * {@code numberOfRepayments}; they must not assume installments equal term-in-months.
 */
public final class LoanRepaymentFrequency {

    public static final int MONTHLY_INTERVAL = 1;
    public static final int QUARTERLY_INTERVAL = 3;
    public static final int SEMI_ANNUAL_INTERVAL = 6;

    public static final String MONTHLY_LABEL = "Monthly";
    public static final String QUARTERLY_LABEL = "Quarterly";
    public static final String SEMI_ANNUAL_LABEL = "Semi-Annual";

    private LoanRepaymentFrequency() {}

    public static boolean isMonths(final Integer frequencyType) {
        return frequencyType != null && frequencyType.equals(PeriodFrequencyType.MONTHS.getValue());
    }

    public static boolean isQuarterly(final Integer repaymentEvery, final Integer frequencyType) {
        return isMonths(frequencyType) && repaymentEvery != null && repaymentEvery == QUARTERLY_INTERVAL;
    }

    public static boolean isSemiAnnual(final Integer repaymentEvery, final Integer frequencyType) {
        return isMonths(frequencyType) && repaymentEvery != null && repaymentEvery == SEMI_ANNUAL_INTERVAL;
    }

    public static String displayName(final Integer repaymentEvery, final EnumOptionData repaymentFrequencyType) {
        final Integer typeId = repaymentFrequencyType == null ? null : repaymentFrequencyType.getId() == null ? null
                : repaymentFrequencyType.getId().intValue();
        return displayName(repaymentEvery, typeId, repaymentFrequencyType == null ? null : repaymentFrequencyType.getValue());
    }

    public static String displayName(final Integer repaymentEvery, final Integer frequencyType, final String frequencyTypeValue) {
        if (isMonths(frequencyType) && repaymentEvery != null) {
            if (repaymentEvery == MONTHLY_INTERVAL) {
                return MONTHLY_LABEL;
            }
            if (repaymentEvery == QUARTERLY_INTERVAL) {
                return QUARTERLY_LABEL;
            }
            if (repaymentEvery == SEMI_ANNUAL_INTERVAL) {
                return SEMI_ANNUAL_LABEL;
            }
        }
        if (repaymentEvery == null && frequencyTypeValue == null) {
            return null;
        }
        if (repaymentEvery == null) {
            return frequencyTypeValue;
        }
        if (frequencyTypeValue == null) {
            return String.valueOf(repaymentEvery);
        }
        return repaymentEvery + " " + frequencyTypeValue;
    }

    public static Integer termInMonths(final Integer termFrequency, final Integer termFrequencyType) {
        if (termFrequency == null || termFrequencyType == null) {
            return null;
        }
        if (termFrequencyType.equals(PeriodFrequencyType.MONTHS.getValue())) {
            return termFrequency;
        }
        if (termFrequencyType.equals(PeriodFrequencyType.YEARS.getValue())) {
            return termFrequency * 12;
        }
        return null;
    }

    /**
     * Products do not store a separate loan term; implied term is {@code numberOfRepayments * repaymentEvery} months.
     * Returns null when the term is not an exact multiple of the interval.
     */
    public static Integer installmentCount(final Integer termMonths, final Integer repaymentEvery) {
        if (termMonths == null || repaymentEvery == null || repaymentEvery <= 0 || termMonths % repaymentEvery != 0) {
            return null;
        }
        return termMonths / repaymentEvery;
    }

    public static void validateProduct(final List<ApiParameterError> dataValidationErrors, final Integer numberOfRepayments,
            final Integer repaymentEvery, final Integer repaymentFrequencyType) {
        if (numberOfRepayments != null && numberOfRepayments < 1) {
            dataValidationErrors.add(ApiParameterError.parameterError("validation.msg.loanproduct.numberOfRepayments.must.be.at.least.one",
                    "Number of installments must be at least 1.", "numberOfRepayments", numberOfRepayments));
        }
        if (!isMonths(repaymentFrequencyType) || repaymentEvery == null || numberOfRepayments == null || numberOfRepayments < 1) {
            return;
        }
        final int impliedTermMonths = numberOfRepayments * repaymentEvery;
        addMinimumTermErrors(dataValidationErrors, impliedTermMonths, repaymentEvery, "numberOfRepayments", true);
    }

    public static void validateLoan(final List<ApiParameterError> dataValidationErrors, final Integer loanTermFrequency,
            final Integer loanTermFrequencyType, final Integer numberOfRepayments, final Integer repaymentEvery,
            final Integer repaymentFrequencyType) {
        if (numberOfRepayments != null && numberOfRepayments < 1) {
            dataValidationErrors.add(ApiParameterError.parameterError("validation.msg.loan.numberOfRepayments.must.be.at.least.one",
                    "Number of installments must be at least 1.", "numberOfRepayments", numberOfRepayments));
        }
        if (!isMonths(repaymentFrequencyType) || repaymentEvery == null) {
            return;
        }
        final Integer termMonths = termInMonths(loanTermFrequency, loanTermFrequencyType);
        if (termMonths == null) {
            return;
        }
        addMinimumTermErrors(dataValidationErrors, termMonths, repaymentEvery, "loanTermFrequency", false);
        if ((repaymentEvery == QUARTERLY_INTERVAL || repaymentEvery == SEMI_ANNUAL_INTERVAL) && termMonths >= repaymentEvery
                && termMonths % repaymentEvery != 0) {
            dataValidationErrors.add(ApiParameterError.parameterError("validation.msg.loan.loanTermFrequency.not.a.multiple.of.repayment.interval",
                    "Loan term must be a multiple of the repayment interval. For " + intervalName(repaymentEvery)
                            + " repayments, loan term must be a multiple of " + repaymentEvery + " months.",
                    "loanTermFrequency", loanTermFrequency, repaymentEvery));
        }
    }

    private static void addMinimumTermErrors(final List<ApiParameterError> dataValidationErrors, final int termMonths,
            final int repaymentEvery, final String parameterName, final boolean product) {
        if (repaymentEvery != QUARTERLY_INTERVAL && repaymentEvery != SEMI_ANNUAL_INTERVAL) {
            return;
        }
        if (termMonths >= repaymentEvery) {
            return;
        }
        final String resource = product ? "loanproduct" : "loan";
        if (repaymentEvery == QUARTERLY_INTERVAL) {
            dataValidationErrors.add(ApiParameterError.parameterError(
                    "validation.msg." + resource + ".repaymentFrequency.incompatible.with.loan.term.quarterly",
                    "Repayment frequency is incompatible with loan term. For quarterly repayments, loan term must be at least 3 months.",
                    parameterName, termMonths));
        } else {
            dataValidationErrors.add(ApiParameterError.parameterError(
                    "validation.msg." + resource + ".repaymentFrequency.incompatible.with.loan.term.semiannual",
                    "Repayment frequency is incompatible with loan term. For semi-annual repayments, loan term must be at least 6 months.",
                    parameterName, termMonths));
        }
    }

    private static String intervalName(final int repaymentEvery) {
        if (repaymentEvery == QUARTERLY_INTERVAL) {
            return "quarterly";
        }
        if (repaymentEvery == SEMI_ANNUAL_INTERVAL) {
            return "semi-annual";
        }
        return "monthly";
    }
}
