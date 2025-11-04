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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import okhttp3.Credentials;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.portfolio.client.domain.ClientOtherInfo;
import org.apache.fineract.portfolio.client.domain.ClientOtherInfoRepository;
import org.apache.fineract.portfolio.client.exception.ClientOtherInfoNotFoundException;
import org.apache.fineract.portfolio.loanaccount.data.DisbursementRequestData;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCharge;
import org.apache.fineract.portfolio.loanaccount.exception.LoanDisbursementRequestException;
import org.apache.fineract.portfolio.note.domain.Note;
import org.apache.fineract.portfolio.note.domain.NoteRepository;
import org.apache.fineract.portfolio.paymenttype.data.PaymentTypeData;
import org.apache.fineract.portfolio.paymenttype.service.PaymentTypeReadPlatformService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DisbursementRequestServiceImpl implements DisbursementRequestService {

    private static final Logger LOG = LoggerFactory.getLogger(TransUnionCrbServiceImpl.class);

    private final ClientOtherInfoRepository clientOtherInfoRepository;

    private final PaymentTypeReadPlatformService paymentTypeReadPlatformService;

    private final NoteRepository noteRepository;
    private OkHttpClient client = new OkHttpClient();
    private Gson gson = new Gson();
    @Autowired
    private Environment env;

    private String authenticateToIntegrationApi() {

        String credential = Credentials.basic(getConfigProperty("fineract.integrations.inkomoko.rest.username"),
                getConfigProperty("fineract.integrations.inkomoko.rest.password"));

        Request request = new Request.Builder().url(getConfigProperty("fineract.integrations.inkomoko.rest.authenticationUrl"))
                .header("Authorization", credential).build();
        Gson gson = new GsonBuilder().create();
        OkHttpClient client = new OkHttpClient();
        Response response = null;
        try {
            response = client.newCall(request).execute();
            String resObject = response.body().string();
            if (response.isSuccessful()) {

                JsonObject jsonResponse = JsonParser.parseString(resObject).getAsJsonObject();
                String accessToken = jsonResponse.get("access_token").getAsString();

                LOG.info("Login to inkomoko Integration  is Successful");
                return accessToken;
            } else {
                LOG.error("Login to inkomoko Integration has failed:" + resObject);
                throw new LoanDisbursementRequestException("Login to inkomoko Integration has failed",
                        "integration.disbursementRequest.loginFailed", resObject);
            }
        } catch (Exception e) {
            LOG.error("Login to inkomoko Integration has failed:" + e);
            throw new LoanDisbursementRequestException("Login to inkomoko Integration has failed",
                    "integration.disbursementRequest.loginFailed", e);
        }
    }

    private String getConfigProperty(String propertyName) {
        return this.env.getProperty(propertyName);
    }

    @Override
    public void disburseRequestLoan(Loan loan, JsonCommand command) {
        String token = authenticateToIntegrationApi();
        final ClientOtherInfo clientOtherInfo = this.clientOtherInfoRepository.getByClientId(loan.client().getId());
        if (clientOtherInfo == null) {
            throw new ClientOtherInfoNotFoundException(null, loan.client().getId());
        }

        BigDecimal totalDisbursementCharge = getDisbursementChargeAmount(loan);

        if (totalDisbursementCharge.compareTo(loan.getPrincpal().getAmount()) > 0) {
            throw new LoanDisbursementRequestException("Disbursement charge is greater than the loan amount ",
                    "integration.disbursementRequest.chargeGreaterThanLoanAmount");
        }

        BigDecimal totalPrincipalToBeDisbursed = loan.getPrincpal().getAmount().subtract(totalDisbursementCharge);
        LOG.info(" Loan Id :=>  [ " + loan.getId() + " ]  Original Principal  [" + loan.getPrincpal().getAmount() + "  ]  Currency   [ "
                + loan.getPrincpal().getCurrencyCode() + "  ]  Total Principal to be disbursed to middleware  ==>  ["
                + totalPrincipalToBeDisbursed + " ]  Total Disbursement Charge  ==>  " + totalDisbursementCharge);

        Long paymentTypeId = command.longValueOfParameterNamed("paymentTypeId");
        final PaymentTypeData paymentTypes = this.paymentTypeReadPlatformService.retrieveOne(paymentTypeId);
        String uniqueId = UUID.randomUUID().toString() + "-" + System.currentTimeMillis();
        final String requestId = "cbs_" + loan.getId() + "_" + uniqueId;
        DisbursementRequestData disbursementRequestData = new DisbursementRequestData(requestId, loan.getAccountNumber(),
                totalPrincipalToBeDisbursed, loan.getPrincpal().getCurrencyCode(), paymentTypes.getName(), clientOtherInfo.getTelephoneNo(),
                clientOtherInfo.getBankAccountNumber(), clientOtherInfo.getBankName(), "CBS", paymentTypeId);

        MediaType JSON = MediaType.get("application/json; charset=utf-8");
        String requestJson = gson.toJson(disbursementRequestData);

        RequestBody body = RequestBody.create(requestJson, JSON);
        Request request = new Request.Builder().url(getConfigProperty("fineract.integrations.inkomoko.rest.initiate.disbursement"))
                .addHeader("Authorization", "Bearer " + token).post(body).build();
        final Note requestNote = Note.loanNote(loan, requestJson);
        this.noteRepository.saveAndFlush(requestNote);
        try (Response response = client.newCall(request).execute()) {
            String responseBody = null;
            if (response.body() != null) {
                responseBody = response.body().string();
            }
            if (response.isSuccessful()) {
                LOG.info("Received Response from Inkomoko for request   " + loan.getId() + " and  loanid  " + requestId);
                final Note responseNote = Note.loanNote(loan, response.toString() + " " + responseBody);
                this.noteRepository.saveAndFlush(responseNote);
            } else {
                Integer responseCode = response.code();
                throw new LoanDisbursementRequestException("Unprocessable Entity", "integration.disbursementRequest.unprocessableEntity",
                        requestId, responseCode, responseBody);
            }

        } catch (IOException e) {
            throw new LoanDisbursementRequestException("Unexpected response received  from  inkomoko ", "loan", e);
        }
    }

    public static BigDecimal getDisbursementChargeAmount(Loan loan) {
        BigDecimal chargeAmount = BigDecimal.ZERO;
        final Collection<LoanCharge> loanCharges = loan.getLoanCharges();

        for (final LoanCharge loanCharge : loanCharges) {
            if (loanCharge.isDueAtDisbursement() && loanCharge.isChargePending()) {
                chargeAmount = chargeAmount.add(loanCharge.amount());
            }
        }
        return chargeAmount;
    }
}
