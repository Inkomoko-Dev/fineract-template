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
package org.apache.fineract.portfolio.loanaccount.data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import org.apache.fineract.infrastructure.core.data.EnumOptionData;
import org.apache.fineract.organisation.monetary.data.CurrencyData;
import org.apache.fineract.portfolio.paymenttype.data.PaymentTypeData;
import org.apache.fineract.useradministration.data.AppUserData;

/**
 * Immutable data object representing a loan transaction.
 */
public class LoanApprovalData {

    private final LocalDate approvalDate;
    private final BigDecimal approvalAmount;
    private final BigDecimal netDisbursalAmount;

    // import fields
    private LocalDate approvedOnDate;
    private String note;
    private String dateFormat;
    private String locale;
    private transient Integer rowIndex;
    private Collection<EnumOptionData> termFrequencyTypeOptions;

    private Collection<AppUserData> approverOptions;
    private CurrencyData currency;
    private LoanDecisionData loanDecisionData;
    private Collection<PaymentTypeData> paymentTypeOptions;
    private Boolean nextApproverRequired;
    private Integer predictedNextStage;
    private BigDecimal maxRecommendedAmount;
    private BigDecimal dueDiligenceRecommendedAmount;
    private Integer dueDiligenceTermFrequency;
    private Integer dueDiligenceTermFrequencyType;
    private Boolean ideaClient;

    public static LoanApprovalData importInstance(LocalDate approvedOnDate, Integer rowIndex, String locale, String dateFormat) {
        return new LoanApprovalData(approvedOnDate, rowIndex, locale, dateFormat);
    }

    private LoanApprovalData(LocalDate approvedOnDate, Integer rowIndex, String locale, String dateFormat) {
        this.approvedOnDate = approvedOnDate;
        this.rowIndex = rowIndex;
        this.dateFormat = dateFormat;
        this.locale = locale;
        this.note = "";
        this.approvalAmount = null;
        this.approvalDate = null;
        this.netDisbursalAmount = null;
    }

    public LoanApprovalData(final BigDecimal approvalAmount, final LocalDate approvalDate, final BigDecimal netDisbursalAmount, final Collection<PaymentTypeData> paymentOptions) {
        this.approvalDate = approvalDate;
        this.approvalAmount = approvalAmount;
        this.netDisbursalAmount = netDisbursalAmount;
        this.paymentTypeOptions = paymentOptions;
    }

    public LoanApprovalData(BigDecimal approvalAmount, LocalDate approvalDate, BigDecimal netDisbursalAmount,
            Collection<EnumOptionData> termFrequencyTypeOptions, CurrencyData currency, LoanDecisionData loanDecisionData, Collection<AppUserData> approverOptions) {
        this.approvalDate = approvalDate;
        this.approvalAmount = approvalAmount;
        this.netDisbursalAmount = netDisbursalAmount;
        this.termFrequencyTypeOptions = termFrequencyTypeOptions;
        this.approverOptions = approverOptions;
        this.currency = currency;
        this.loanDecisionData = loanDecisionData;
    }

    public LocalDate getApprovalDate() {
        return this.approvalDate;
    }

    public BigDecimal getApprovalAmount() {
        return this.approvalAmount;
    }

    public BigDecimal getNetDisbursalAmount() {
        return this.netDisbursalAmount;
    }

    public void setApproverOptionsOptions(Collection<AppUserData> approvers) {
        this.approverOptions = approvers;
    }

    public Collection<AppUserData> getApproverOptions() {
        return this.approverOptions;
    }

    public Collection<EnumOptionData> getTermFrequencyTypeOptions() {
        return this.termFrequencyTypeOptions;
    }

    public CurrencyData getCurrency() {
        return this.currency;
    }

    public LoanDecisionData getLoanDecisionData() {
        return this.loanDecisionData;
    }

    public Boolean getNextApproverRequired() {
        return this.nextApproverRequired;
    }

    public void setNextApproverRequired(Boolean nextApproverRequired) {
        this.nextApproverRequired = nextApproverRequired;
    }

    public Integer getPredictedNextStage() {
        return this.predictedNextStage;
    }

    public void setPredictedNextStage(Integer predictedNextStage) {
        this.predictedNextStage = predictedNextStage;
    }

    public BigDecimal getMaxRecommendedAmount() {
        return this.maxRecommendedAmount;
    }

    public void setMaxRecommendedAmount(BigDecimal maxRecommendedAmount) {
        this.maxRecommendedAmount = maxRecommendedAmount;
    }

    public BigDecimal getDueDiligenceRecommendedAmount() {
        return this.dueDiligenceRecommendedAmount;
    }

    public void setDueDiligenceRecommendedAmount(BigDecimal dueDiligenceRecommendedAmount) {
        this.dueDiligenceRecommendedAmount = dueDiligenceRecommendedAmount;
    }

    public Integer getDueDiligenceTermFrequency() {
        return this.dueDiligenceTermFrequency;
    }

    public void setDueDiligenceTermFrequency(Integer dueDiligenceTermFrequency) {
        this.dueDiligenceTermFrequency = dueDiligenceTermFrequency;
    }

    public Integer getDueDiligenceTermFrequencyType() {
        return this.dueDiligenceTermFrequencyType;
    }

    public void setDueDiligenceTermFrequencyType(Integer dueDiligenceTermFrequencyType) {
        this.dueDiligenceTermFrequencyType = dueDiligenceTermFrequencyType;
    }

    public Boolean getIdeaClient() {
        return this.ideaClient;
    }

    public void setIdeaClient(Boolean ideaClient) {
        this.ideaClient = ideaClient;
    }

}
