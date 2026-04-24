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
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.commons.collections.CollectionUtils;
import org.apache.fineract.infrastructure.core.exception.CrbBusinessRuleException;
import org.apache.fineract.infrastructure.core.exception.CrbLocalValidationException;
import org.apache.fineract.infrastructure.core.exception.CrbPreSubmissionValidationException;
import org.apache.fineract.infrastructure.core.exception.CrbSystemException;
import org.apache.fineract.infrastructure.core.exception.CrbValidationException;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.jobs.annotation.CronTarget;
import org.apache.fineract.infrastructure.jobs.exception.JobExecutionException;
import org.apache.fineract.infrastructure.jobs.service.JobName;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.loanaccount.data.RwandaConsumerCreditData;
import org.apache.fineract.portfolio.loanaccount.data.RwandaCorporateCreditData;
import org.apache.fineract.portfolio.loanaccount.data.TransUnionAuthenticationData;
import org.apache.fineract.portfolio.loanaccount.data.TransUnionRwandaConsumerCreditData;
import org.apache.fineract.portfolio.loanaccount.data.TransUnionRwandaCorporateCreditData;
import org.apache.fineract.portfolio.loanaccount.domain.CRBPostingLoggerRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.domain.TransunionCrbCorporateLoggerRepository;
import org.apache.fineract.portfolio.loanaccount.domain.TransunionCrbConsumerLoggerRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanStatus;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.CRBPostingLogger;
import org.apache.fineract.useradministration.domain.AppUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
@Slf4j
public class TransUnionCrbServiceImpl implements TransUnionCrbService {

    private static final Logger LOG = LoggerFactory.getLogger(TransUnionCrbServiceImpl.class);
    private static final String DEFAULT_BALANCE_INDICATOR = "D";
    private static final int MAX_DAYS_IN_ARREARS_FOR_CURRENT_INDICATOR = 90;
    public static final String FORM_URL_CONTENT_TYPE = "application/json";
    private final TransUnionCrbPostConsumerCreditReadPlatformServiceImpl transUnionCrbPostConsumerCreditReadPlatformServiceImpl;
    private final TransUnionCrbPostCorporateCreditReadPlatformServiceImpl transUnionCrbPostCorporateCreditReadPlatformServiceImpl;
    private final LoanRepositoryWrapper loanRepository;
    private final TransunionCrbConsumerLoggerRepository crbConsumerLoggerRepository;
    private final TransunionCrbCorporateLoggerRepository crbCorporateLoggerRepository;
    private  final CRBPostingLoggerRepository crbPostingLoggerRepository;
    private final PlatformSecurityContext context;
    @Autowired
    private Environment env;

    @Override
    @CronTarget(jobName = JobName.POST_RWANDA_CONSUMER_CREDIT_TO_TRANSUNION_CRB)
    public void ConsumerCreditDataUploadToTransUnion() {

        LOG.info("Starting Consumer Credit Data Upload To TransUnion CRB");
        final AppUser currentUser = this.context.authenticatedUser();

        String batchId = UUID.randomUUID().toString();
        LocalDate date = LocalDate.now(ZoneId.systemDefault());
        long lastLoanId = 0L;
        final int pageSize = 500;

        while (true) {

            Collection<TransUnionRwandaConsumerCreditData> records =
                    transUnionCrbPostConsumerCreditReadPlatformServiceImpl.retrieveAllConsumerCreditsPage(lastLoanId, pageSize);
            LOG.info(">>>> Size for Consumer credit -> {}", records.size());

            if (CollectionUtils.isEmpty(records)) {
                break;
            }

            List<Integer> loansNotToBeRePostedTransUnion = new ArrayList<>();

            String token = authenticateToTransUnionRestApi();

            for (TransUnionRwandaConsumerCreditData creditData : records) {

                try {
                    String payload = null;

                    try {
                        validateConsumerAddressForCrb(creditData);

                        RwandaConsumerCreditData rwandaConsumerCreditData = new RwandaConsumerCreditData();
                        rwandaConsumerCreditData.setConsumerCreditInformationRecord(creditData);
                        rwandaConsumerCreditData.setRecordType("IC");

                        payload = convertConsumerCreditPayloadToJson(rwandaConsumerCreditData);

                        String callbackId = postRwandaConsumerCreditToTransUnion(token, payload);

                        // success
                        saveCrbPostingLogger(
                                creditData.getLoanId(),
                                batchId,
                                callbackId,
                                true,
                                null,
                                payload,
                                currentUser,
                                date
                        );

                        // closed loans must never be resent
                        if (!creditData.getLoanStatus().equals(LoanStatus.ACTIVE.getValue())) {
                            loansNotToBeRePostedTransUnion.add(creditData.getLoanId());
                        }
                    }

                    // Data / business rejection → log and STOP reposting
                    catch (CrbLocalValidationException | CrbValidationException | CrbBusinessRuleException e) {
                        LOG.info("Consumer credit rejected during CRB validation for loanId={}", creditData.getLoanId());

                        saveCrbPostingLogger(
                                creditData.getLoanId(),
                                batchId,
                                e.getCallbackId(),
                                false,
                                e.getUserMessage(),
                                payload,
                                currentUser,
                                date
                        );
                    }

                    // TransUnion or network failure → retry later
                    catch (CrbSystemException e) {
                        log.info("System error when posting consumer credit for loanId={}: {}", creditData.getLoanId(), e.getMessage());
                        saveCrbPostingLogger(
                                creditData.getLoanId(),
                                batchId,
                                e.getCallbackId(),
                                false,
                                "TransUnion error: " + e.getMessage(),
                                payload,
                                currentUser,
                                date
                        );

                        throw e;   // Quartz must retry
                    }

                    lastLoanId = creditData.getLoanId();

                    Thread.sleep(200); // Sleep to respect rate limit

                }catch (InterruptedException e){
                    log.error("Thread interrupted while waiting for semaphore permit", e);
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            }

            // Update flags only for loans that must never be resent
            for (Integer loanId : loansNotToBeRePostedTransUnion) {
                Loan loan = loanRepository.findOneWithNotFoundDetection(loanId.longValue());
                loan.setStopConsumerCreditUploadToTransUnion(Boolean.TRUE);
                loan.setStopConsumerCreditUploadToTransUnionOn(DateUtils.getBusinessLocalDate());
                loanRepository.saveAndFlush(loan);
            }

        }

    }

    @Async
    protected void saveCrbPostingLogger(Integer loanId, String batchId, String callbackId, Boolean hasPassed, String errorLogs,
                                        String payload, AppUser currentUser,LocalDate date){
        CRBPostingLogger logger = new CRBPostingLogger(batchId, hasPassed, loanId, callbackId, errorLogs, payload);

        assert currentUser.getId() != null;
        logger.setCreatedBy(currentUser.getId());
        logger.setLastModifiedBy(currentUser.getId());
        logger.setDate(date);
        crbPostingLoggerRepository.saveAndFlush(logger);

    }


    @Override
    @CronTarget(jobName = JobName.POST_RWANDA_CORPORATE_CREDIT_TO_TRANSUNION_CRB)
    public void CorporateCreditDataUploadToTransUnion() {

        LOG.info("Starting Corporate Credit Data Upload To TransUnion CRB");

        final AppUser currentUser = this.context.authenticatedUser();

        long lastLoanId = 0L;
        final int pageSize = 500;

        String batchId = UUID.randomUUID().toString();
        LocalDate date = LocalDate.now(ZoneId.systemDefault());

        while (true) {
            Collection<TransUnionRwandaCorporateCreditData> records =
                    transUnionCrbPostCorporateCreditReadPlatformServiceImpl.retrieveAllCorporateCreditsPage(lastLoanId, pageSize);
            LOG.info(">>>> Size for Corporate credit -> {}", records.size());

            if (CollectionUtils.isEmpty(records)) {
                break;
            }

            List<Integer> loansNotToBeRePostedTransUnion = new ArrayList<>();

            String token = authenticateToTransUnionRestApi();

            for (TransUnionRwandaCorporateCreditData creditData : records) {

                try{
                    String payload = null;

                    try {
                        validateCorporateAddressForCrb(creditData);

                        RwandaCorporateCreditData rwandaCorporateCreditData = new RwandaCorporateCreditData();
                        rwandaCorporateCreditData.setCorporateCreditInformationRecord(creditData);
                        rwandaCorporateCreditData.setRecordType("CI");

                        payload = convertConsumerCreditPayloadToJson(rwandaCorporateCreditData);

                        validateCorporateCreditRecord(creditData);
                        String callbackId = postRwandaCorporateCreditToTransUnion(token, payload);

                        // success
                        saveCrbPostingLogger(
                                creditData.getLoanId(),
                                batchId,
                                callbackId,
                                true,
                                null,
                                payload,
                                currentUser,
                                date
                        );

                        // closed / non-active loans must never be resent
                        if (!creditData.getLoanStatus().equals(LoanStatus.ACTIVE.getValue())) {
                            loansNotToBeRePostedTransUnion.add(creditData.getLoanId());
                        }
                    }

                    // Business / validation rejection → log and STOP reposting
                    catch (CrbLocalValidationException | CrbValidationException | CrbBusinessRuleException e) {

                        LOG.info("Corporate credit rejected during CRB validation for loanId={}", creditData.getLoanId());

                        saveCrbPostingLogger(
                                creditData.getLoanId(),
                                batchId,
                                e.getCallbackId(),
                                false,
                                e.getUserMessage(),
                                payload,
                                currentUser,
                                date
                        );
                    }

                    // TransUnion / network / infra failure → retry later
                    catch (CrbSystemException e) {

                        saveCrbPostingLogger(
                                creditData.getLoanId(),
                                batchId,
                                e.getCallbackId(),
                                false,
                                "TransUnion system error: " + e.getMessage(),
                                payload,
                                currentUser,
                                date
                        );

                        throw e; // Quartz must retry
                    }

                    lastLoanId = creditData.getLoanId();

                    Thread.sleep(200);

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }

            }

            // Update flags only for loans that must never be resent
            for (Integer loanId : loansNotToBeRePostedTransUnion) {
                Loan loan = loanRepository.findOneWithNotFoundDetection(loanId.longValue());
                loan.setStopConsumerCreditUploadToTransUnion(Boolean.TRUE);
                loan.setStopConsumerCreditUploadToTransUnionOn(DateUtils.getBusinessLocalDate());
                loanRepository.saveAndFlush(loan);
            }

        }

    }

    void validateConsumerAddressForCrb(TransUnionRwandaConsumerCreditData creditData) {
        validateSelectedAddressForCrb(creditData.getAccountNumber(), creditData.getSelectedAddressId(),
                creditData.getSelectedAddressType(), creditData.getCountry());
    }

    void validateCorporateAddressForCrb(TransUnionRwandaCorporateCreditData creditData) {
        validateSelectedAddressForCrb(creditData.getAccountNumber(), creditData.getSelectedAddressId(),
                creditData.getSelectedAddressType(), creditData.getCountry());
    }

    private void validateSelectedAddressForCrb(String accountNumber, Long selectedAddressId, String selectedAddressType, String country) {
        if (selectedAddressId == null) {
            throw new CrbLocalValidationException(buildLocalValidationMessage(accountNumber,
                    "the client has no address available for CRB posting. Country must come from the preferred client address, prioritizing active and current or primary address types."),
                    null);
        }

        if (country == null || country.isBlank()) {
            final String addressType = (selectedAddressType == null || selectedAddressType.isBlank()) ? "selected address"
                    : selectedAddressType;
            throw new CrbLocalValidationException(buildLocalValidationMessage(accountNumber,
                    "the selected " + addressType + " has no country. Country must come from the address country field."), null);
        }
    }

    private String buildLocalValidationMessage(String accountNumber, String detail) {
        if (accountNumber == null || accountNumber.isBlank()) {
            return "CRB posting skipped because " + detail;
        }
        return "Loan " + accountNumber + " cannot be posted to CRB because " + detail;
    }

    void validateCorporateCreditRecord(TransUnionRwandaCorporateCreditData creditData) {
        final String indicator = Objects.toString(creditData.getCurrentBalanceIndicator(), "");
        final int daysInArrears = Objects.requireNonNullElse(creditData.getDaysInArrears(), 0);

        if (!DEFAULT_BALANCE_INDICATOR.equals(indicator) || daysInArrears > MAX_DAYS_IN_ARREARS_FOR_CURRENT_INDICATOR) {
            return;
        }

        final String loanReference = Objects.toString(creditData.getAccountNumber(), String.valueOf(creditData.getLoanId()));
        final String message = String.format(
                "Corporate CRB submission blocked before sending for Loan %s. Invalid Current Balance Indicator / Days in Arrears combination: indicator '%s' requires days in arrears greater than %d, but the payload has %d. Review the corporate CRB mapping and source arrears data before retrying.",
                loanReference,
                indicator,
                MAX_DAYS_IN_ARREARS_FOR_CURRENT_INDICATOR,
                daysInArrears
        );

        throw new CrbPreSubmissionValidationException(
                loanReference,
                "Current Balance Indicator / Days in Arrears",
                indicator + " / " + daysInArrears,
                message
        );
    }


    private String convertConsumerCreditPayloadToJson(RwandaConsumerCreditData rwandaConsumerCreditData) {
        Gson gson = new GsonBuilder().create();
        String request = gson.toJson(rwandaConsumerCreditData);
        LOG.info("Actual Payload to be sent - - >" + request);
        return request;
    }

    private String convertConsumerCreditPayloadToJson(RwandaCorporateCreditData rwandaCorporateCreditData) {
        Gson gson = new GsonBuilder().create();
        String request = gson.toJson(rwandaCorporateCreditData);
        LOG.info("Corporate --> Actual Payload to be sent - - >" + request);
        return request;
    }

    private String authenticateToTransUnionRestApi() {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(getConfigProperty("fineract.integrations.transUnion.crb.rest.authenticationUrl"))
                .newBuilder();
        String url = urlBuilder.build().toString();

        TransUnionAuthenticationData transUnionAuthenticationData = new TransUnionAuthenticationData(
                getConfigProperty("fineract.integrations.transUnion.crb.rest.username"),
                getConfigProperty("fineract.integrations.transUnion.crb.rest.password"),
                getConfigProperty("fineract.integrations.transUnion.crb.rest.infinityCode"));
        Gson gson = new GsonBuilder().create();
        RequestBody formBody = RequestBody.create(MediaType.parse(FORM_URL_CONTENT_TYPE), gson.toJson(transUnionAuthenticationData));

        OkHttpClient client = new OkHttpClient();
        Response response = null;
        Request request = new Request.Builder().url(url).post(formBody).build();

        List<Throwable> exceptions = new ArrayList<>();

        try {
            response = client.newCall(request).execute();
            String resObject = response.body().string();
            if (response.isSuccessful()) {

                JsonObject jsonResponse = JsonParser.parseString(resObject).getAsJsonObject();
                String accessToken = jsonResponse.get("token").getAsString();

                log.info("Login to CRB TransUnion is Successful");

                return accessToken;
            } else {
                log.error("Login to CRB TransUnion failed with Message:" + resObject);

                handleAPIIntegrityIssues(resObject);

            }
        } catch (Exception e) {
            log.error("Authentication to CRB TransUnion has failed" + e);
            exceptions.add(e);
        }
        if (!CollectionUtils.isEmpty(exceptions)) {
            try {
                throw new JobExecutionException(exceptions);
            } catch (JobExecutionException e) {
                throw new RuntimeException(e);
            }
        }
        return null;
    }


    private void handleAPIIntegrityIssues(String httpResponse) {

        log.info("http response: {}", httpResponse);

        JsonObject json;
        try {
            json = JsonParser.parseString(httpResponse).getAsJsonObject();
        } catch (Exception ex) {
            throw new CrbSystemException("Invalid JSON response from TransUnion", null);
        }

        final int code = getAsInt(json, "responseCode", -1);
        final String callbackId = getAsString(json, "callbackId", null);

        switch (code) {
            case 200:
                return;

            case 600: {
                final JsonArray errors = getAsArray(json, "recordErrors");
                if (errors == null || errors.size() == 0) {
                    throw new CrbValidationException(
                            null,
                            null,
                            null,
                            "Validation failed but no recordErrors provided",
                            callbackId
                    );
                }

                final JsonObject firstError = safeGetObject(errors, 0);

                final String accountNumber = getAsString(firstError, "accountNumber", null);
                final String fieldName     = getAsString(firstError, "fieldName", null);
                final String fieldValue    = getAsString(firstError, "fieldValue", null);

                final Set<String> uniqueMessages = new LinkedHashSet<>();

                for (int i = 0; i < errors.size(); i++) {
                    JsonObject err = safeGetObject(errors, i);
                    String message = getAsString(err, "errorMessage", null);

                    if (message == null || message.isBlank()) {
                        continue;
                    }

                    message = message.replaceAll("\\[ADVICE.*?\\]", "").trim();
                    if (!message.isBlank()) {
                        uniqueMessages.add(message);
                    }
                }

                final StringBuilder userMessage = new StringBuilder();
                userMessage.append("CRB submission failed");

                if (accountNumber != null && !accountNumber.isBlank()) {
                    userMessage.append(" for Loan ").append(accountNumber);
                }

                userMessage.append(".\n\n");

                if (fieldName != null) {
                    userMessage.append("Field: ").append(fieldName).append("\n");
                }
                if (fieldValue != null) {
                    userMessage.append("Current value: ").append(fieldValue).append("\n");
                }

                userMessage.append("\nIssue(s):\n");
                if (uniqueMessages.isEmpty()) {
                    userMessage.append("• Validation failed (no detailed messages provided)\n");
                } else {
                    uniqueMessages.forEach(msg -> userMessage.append("• ").append(msg).append("\n"));
                }

                userMessage.append("\nAction required:\n");
                if (fieldName != null) {
                    userMessage.append("• Correct the ").append(fieldName).append("\n");
                } else {
                    userMessage.append("• Correct the invalid field(s)\n");
                }
                userMessage.append("• Ensure it meets CRB format requirements\n");

                throw new CrbValidationException(
                        accountNumber,
                        fieldName,
                        fieldValue,
                        userMessage.toString(),
                        callbackId
                );
            }

            default: {
                final String message = getAsString(json, "message",
                        (code >= 400 && code < 500)
                                ? "TransUnion rejected the request"
                                : "Unexpected TransUnion response");

                if (code >= 400 && code < 500) {
                    throw new CrbBusinessRuleException(message, callbackId);
                } else {
                    throw new CrbSystemException(message, callbackId);
                }
            }
        }
    }

    /** Safe helpers **/
    private static String getAsString(JsonObject obj, String key, String defaultValue) {
        if (obj == null || key == null || !obj.has(key)) return defaultValue;
        JsonElement el = obj.get(key);
        if (el == null || el.isJsonNull()) return defaultValue;

        try {
            return el.getAsString();
        } catch (Exception e) {
            // In case it's not a primitive string (e.g., object/array)
            return el.toString();
        }
    }

    private static int getAsInt(JsonObject obj, String key, int defaultValue) {
        if (obj == null || key == null || !obj.has(key)) return defaultValue;
        JsonElement el = obj.get(key);
        if (el == null || el.isJsonNull()) return defaultValue;

        try {
            return el.getAsInt();
        } catch (Exception e) {
            // sometimes API returns numeric codes as strings
            try {
                return Integer.parseInt(el.getAsString());
            } catch (Exception ignore) {
                return defaultValue;
            }
        }
    }

    private static JsonArray getAsArray(JsonObject obj, String key) {
        if (obj == null || key == null || !obj.has(key)) return null;
        JsonElement el = obj.get(key);
        if (el == null || el.isJsonNull() || !el.isJsonArray()) return null;
        return el.getAsJsonArray();
    }

    private static JsonObject safeGetObject(JsonArray arr, int idx) {
        if (arr == null || idx < 0 || idx >= arr.size()) return new JsonObject();
        JsonElement el = arr.get(idx);
        if (el == null || el.isJsonNull() || !el.isJsonObject()) return new JsonObject();
        return el.getAsJsonObject();
    }



    private String getConfigProperty(String propertyName) {
        return this.env.getProperty(propertyName);
    }

    private String postRwandaConsumerCreditToTransUnion(
            String accessToken,
            String consumerCreditData) {

        HttpUrl.Builder urlBuilder = Objects.requireNonNull(HttpUrl
                        .parse(getConfigProperty("fineract.integrations.transUnion.crb.rest.postConsumerCredit")))
                .newBuilder();

        String url = urlBuilder.build().toString();
        OkHttpClient client = new OkHttpClient();

        RequestBody body = RequestBody.create(
                MediaType.parse("application/json"),
                consumerCreditData
        );

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + accessToken)
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {

            assert response.body() != null;

            String resObject = response.body().string();

            log.info("Consumer Credit Response from TransUnion :=> {}", resObject);

            if (!response.isSuccessful()) {
                throw new CrbSystemException(
                        "HTTP error from TransUnion: " + response.code(),
                        null
                );
            }

            JsonObject jsonResponse = JsonParser.parseString(resObject).getAsJsonObject();

            int code = jsonResponse.has("responseCode")
                    ? jsonResponse.get("responseCode").getAsInt()
                    : -1;

            if (code == 200) {
                return jsonResponse.get("callbackId").getAsString();
            }

            // Handle business / validation issues
            handleAPIIntegrityIssues(resObject);
            return null;

        } catch (IOException e) {
            // Infrastructure failure — FAIL JOB
            log.error("IO failure posting to TransUnion", e);
            throw new RuntimeException(e);

        } catch (CrbSystemException e) {
            // System failure — FAIL JOB
            log.error("CRB system failure", e);
            throw e;
        }
    }


    private String postRwandaCorporateCreditToTransUnion(
            String accessToken,
            String corporateCreditData)  {

        HttpUrl url = HttpUrl.parse(
                getConfigProperty("fineract.integrations.transUnion.crb.rest.postCorporateCredit")
        );

        OkHttpClient client = new OkHttpClient();

        RequestBody requestBody = RequestBody.create(
                MediaType.parse("application/json"),
                corporateCreditData
        );

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + accessToken)
                .post(requestBody)
                .build();

        try (Response response = client.newCall(request).execute()) {

            String responseBody = response.body() != null ? response.body().string() : "";

            log.info("Corporate Credit Response from TransUnion => {}", responseBody);

            // HTTP-level failure → system failure (retryable)
            if (!response.isSuccessful()) {
                throw new CrbSystemException(
                        null,
                        "HTTP " + response.code() + " from TransUnion: " + responseBody
                );
            }

            JsonObject jsonResponse = JsonParser.parseString(responseBody).getAsJsonObject();

            Integer responseCode = jsonResponse.has("responseCode")
                    ? jsonResponse.get("responseCode").getAsInt()
                    : null;

            String callbackId = jsonResponse.has("callbackId")
                    ? jsonResponse.get("callbackId").getAsString()
                    : null;

            // Accepted
            if (Integer.valueOf(200).equals(responseCode)) {
                return callbackId;
            }

            handleAPIIntegrityIssues(responseBody);
            return null;
        }catch (IOException e) {
            // Infrastructure failure — FAIL JOB
            log.error("IO failure posting to TransUnion", e);
            throw new RuntimeException(e);

        }
        catch (CrbSystemException e) {
            // System failure — FAIL JOB
            log.error("CRB system failure", e);
            throw e;
        }
    }

}
