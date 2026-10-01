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
import java.util.HashMap;
import java.util.Map;
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
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.excessrefund.domain.LoanExcessRefund;
import org.apache.fineract.portfolio.loanaccount.service.DisbursementRequestServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

/**
 * Submits excess refund payouts to Payment Hub. Auth and error categorization mirror disbursement flow.
 */
@Service
@RequiredArgsConstructor
public class ExcessRefundPaymentHubService {

    private static final Logger LOG = LoggerFactory.getLogger(ExcessRefundPaymentHubService.class);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final Environment environment;
    private final OkHttpClient client = new OkHttpClient();
    private final Gson gson = new Gson();

    public String submitRefund(final Loan loan, final LoanExcessRefund refund) {
        final String requestId = "cbs_refund_" + refund.getId() + "_" + UUID.randomUUID().toString().substring(0, 8);
        final String token = authenticate();
        final Map<String, Object> payload = buildPayload(loan, refund, requestId);
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

    private Map<String, Object> buildPayload(final Loan loan, final LoanExcessRefund refund, final String requestId) {
        final Map<String, Object> payload = new HashMap<>();
        payload.put("requestId", requestId);
        payload.put("loanId", loan.getId());
        payload.put("accountNo", loan.getAccountNumber());
        payload.put("amount", refund.getAmount());
        payload.put("currency", refund.getCurrencyCode());
        payload.put("transactionType", "REFUND");
        payload.put("paymentTypeId", refund.getPaymentTypeId());
        payload.put("source", "CBS");
        if (StringUtils.isNotBlank(refund.getBeneficiaryJson())) {
            final JsonObject beneficiary = JsonParser.parseString(refund.getBeneficiaryJson()).getAsJsonObject();
            payload.put("beneficiary", beneficiary);
        }
        return payload;
    }

    private String authenticate() {
        final String credential = Credentials.basic(getConfigProperty("fineract.integrations.inkomoko.rest.username"),
                getConfigProperty("fineract.integrations.inkomoko.rest.password"));
        final Request request = new Request.Builder().url(getConfigProperty("fineract.integrations.inkomoko.rest.authenticationUrl"))
                .header("Authorization", credential).post(RequestBody.create(new byte[0], null)).build();
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

    private String getConfigProperty(final String propertyName) {
        return this.environment.getProperty(propertyName);
    }
}
