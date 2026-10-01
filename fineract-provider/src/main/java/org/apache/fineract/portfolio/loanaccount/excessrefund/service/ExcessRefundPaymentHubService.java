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
package org.apache.fineract.portfolio.loanaccount.excessrefund.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import okhttp3.Credentials;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.portfolio.loanaccount.data.DisbursementRequestData;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.excessrefund.domain.LoanExcessRefund;
import org.apache.fineract.portfolio.loanaccount.service.DisbursementRequestServiceImpl;
import org.apache.fineract.portfolio.paymenttype.data.PaymentTypeData;
import org.apache.fineract.portfolio.paymenttype.service.PaymentTypeReadPlatformService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

/**
 * Submits excess refund payouts to Payment Hub. Auth and payload shape mirror disbursement flow
 * ({@link DisbursementRequestServiceImpl}) with {@code transactionType=REFUND}.
 */
@Service
@RequiredArgsConstructor
public class ExcessRefundPaymentHubService {

    private static final Logger LOG = LoggerFactory.getLogger(ExcessRefundPaymentHubService.class);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final Environment environment;
    private final PaymentTypeReadPlatformService paymentTypeReadPlatformService;
    private final OkHttpClient client = new OkHttpClient();
    private final Gson gson = new Gson();

    public String submitRefund(final Loan loan, final LoanExcessRefund refund) {
        final String requestId = "cbs_refund_" + refund.getId() + "_" + UUID.randomUUID().toString().substring(0, 8);
        final String token = authenticate();
        final DisbursementRequestData payload = buildPayload(loan, refund, requestId);
        final String requestJson = this.gson.toJson(payload);
        final String url = getConfigProperty("fineract.integrations.inkomoko.rest.initiate.refund");
        if (StringUtils.isBlank(url)) {
            throw new GeneralPlatformDomainRuleException("integration.excessRefund.config.missing",
                    "Payment Hub refund URL is not configured (fineract.integrations.inkomoko.rest.initiate.refund).");
        }
        final Request request = new Request.Builder().url(url).addHeader("Authorization", "Bearer " + token)
                .post(RequestBody.create(requestJson, JSON)).build();
        try (Response response = this.client.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : null;
            if (response.isSuccessful()) {
                LOG.info("Payment Hub refund accepted loanId={}, refundId={}, requestId={}", loan.getId(), refund.getId(), requestId);
                return requestId;
            }
            final int code = response.code();
            final DisbursementRequestServiceImpl.PaymentHubErrorResponse parsed = DisbursementRequestServiceImpl
                    .parsePaymentHubErrorResponse(responseBody);
            final String category = DisbursementRequestServiceImpl.disbursementFailureCategory(code, parsed);
            final String userMessage = DisbursementRequestServiceImpl.failureMessage(category, parsed);
            LOG.error("Payment Hub refund rejected loanId={}, refundId={}, status={}, body={}", loan.getId(), refund.getId(), code,
                    responseBody);
            throw new GeneralPlatformDomainRuleException("integration.excessRefund." + category, userMessage);
        } catch (IOException e) {
            LOG.error("Payment Hub refund connection failure loanId={}, refundId={}", loan.getId(), refund.getId(), e);
            throw new GeneralPlatformDomainRuleException("integration.excessRefund.connectionFailed",
                    "There was a connection issue while sending the refund to the Payment Hub. Please try again.");
        }
    }

    DisbursementRequestData buildPayload(final Loan loan, final LoanExcessRefund refund, final String requestId) {
        String paymentMethod = "UNKNOWN";
        Long paymentMethodId = refund.getPaymentTypeId() == null ? 0L : refund.getPaymentTypeId();
        if (refund.getPaymentTypeId() != null) {
            try {
                final PaymentTypeData paymentType = this.paymentTypeReadPlatformService.retrieveOne(refund.getPaymentTypeId());
                if (paymentType != null && StringUtils.isNotBlank(paymentType.getName())) {
                    paymentMethod = paymentType.getName();
                }
            } catch (RuntimeException ex) {
                LOG.warn("Unable to resolve payment type {} for excess refund {}", refund.getPaymentTypeId(), refund.getId());
            }
        }

        String phone = null;
        String accountNumber = null;
        String bankName = null;
        String beneficiaryName = null;
        if (StringUtils.isNotBlank(refund.getBeneficiaryJson())) {
            final JsonObject beneficiary = JsonParser.parseString(refund.getBeneficiaryJson()).getAsJsonObject();
            phone = jsonString(beneficiary, "msisdn");
            accountNumber = jsonString(beneficiary, "accountNumber");
            bankName = firstNonBlank(jsonString(beneficiary, "bankName"), jsonString(beneficiary, "bankCode"));
            beneficiaryName = jsonString(beneficiary, "beneficiaryName");
            if (StringUtils.isBlank(phone) && StringUtils.isNotBlank(accountNumber)) {
                phone = accountNumber;
            }
        }

        final DisbursementRequestData payload = new DisbursementRequestData(requestId, loan.getAccountNumber(), refund.getAmount(),
                refund.getCurrencyCode(), paymentMethod, paymentMethodId, StringUtils.defaultIfBlank(phone, "0000000000"), accountNumber,
                bankName, "CBS", paymentMethodId);
        payload.setLoanId(loan.getId());
        payload.setTransactionType("REFUND");
        payload.setBeneficiaryName(beneficiaryName);
        payload.setNarration("Excess refund #" + refund.getId() + " for loan " + loan.getAccountNumber());
        return payload;
    }

    private String authenticate() {
        final String credential = Credentials.basic(getConfigProperty("fineract.integrations.inkomoko.rest.username"),
                getConfigProperty("fineract.integrations.inkomoko.rest.password"));
        // Mirror DisbursementRequestServiceImpl: default GET with basic auth header.
        final Request request = new Request.Builder().url(getConfigProperty("fineract.integrations.inkomoko.rest.authenticationUrl"))
                .header("Authorization", credential).build();
        try (Response response = this.client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new GeneralPlatformDomainRuleException("integration.excessRefund.authFailed",
                        "Unable to authenticate with Payment Hub for excess refund.");
            }
            final JsonObject body = JsonParser.parseString(response.body().string()).getAsJsonObject();
            if (body.has("access_token")) {
                return body.get("access_token").getAsString();
            }
            if (body.has("token")) {
                return body.get("token").getAsString();
            }
            throw new GeneralPlatformDomainRuleException("integration.excessRefund.authFailed",
                    "Payment Hub authentication response missing token.");
        } catch (IOException e) {
            throw new GeneralPlatformDomainRuleException("integration.excessRefund.authFailed",
                    "Unable to authenticate with Payment Hub for excess refund.");
        }
    }

    private static String jsonString(final JsonObject object, final String member) {
        if (object == null || !object.has(member) || object.get(member).isJsonNull()) {
            return null;
        }
        return object.get(member).getAsString();
    }

    private static String firstNonBlank(final String first, final String second) {
        if (StringUtils.isNotBlank(first)) {
            return first;
        }
        return second;
    }

    private String getConfigProperty(final String propertyName) {
        return this.environment.getProperty(propertyName);
    }
}
