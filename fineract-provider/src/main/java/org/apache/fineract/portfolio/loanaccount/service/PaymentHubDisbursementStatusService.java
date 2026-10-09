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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.portfolio.loanaccount.api.LoanDisbursementIntegrationApiResource;
import org.apache.fineract.portfolio.loanaccount.data.PaymentHubDisbursementStatusData;
import org.apache.fineract.portfolio.loanaccount.data.PaymentHubStatusQueryResult;
import org.apache.fineract.portfolio.loanaccount.data.PaymentHubTransactionStatus;
import org.apache.fineract.portfolio.loanaccount.exception.LoanDisbursementRequestException;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanDisbursementDetails;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepository;
import org.apache.fineract.portfolio.loanaccount.domain.PaymentHubDisbursementPoll;
import org.apache.fineract.portfolio.loanaccount.domain.PaymentHubDisbursementPollRepository;
import org.apache.fineract.portfolio.note.domain.Note;
import org.apache.fineract.portfolio.note.domain.NoteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PaymentHubDisbursementStatusService {

    private static final Logger LOG = LoggerFactory.getLogger(PaymentHubDisbursementStatusService.class);

    private final PaymentHubDisbursementPollRepository pollRepository;
    private final DisbursementRequestService disbursementRequestService;
    private final LoanRepository loanRepository;
    private final LoanWritePlatformService loanWritePlatformService;
    private final NoteRepository noteRepository;
    private final FromJsonHelper fromApiJsonHelper;
    private final PlatformTransactionManager transactionManager;

    public PaymentHubDisbursementStatusService(final PaymentHubDisbursementPollRepository pollRepository,
            final DisbursementRequestService disbursementRequestService, final LoanRepository loanRepository,
            final LoanWritePlatformService loanWritePlatformService, final NoteRepository noteRepository,
            final FromJsonHelper fromApiJsonHelper, final PlatformTransactionManager transactionManager) {
        this.pollRepository = pollRepository;
        this.disbursementRequestService = disbursementRequestService;
        this.loanRepository = loanRepository;
        this.loanWritePlatformService = loanWritePlatformService;
        this.noteRepository = noteRepository;
        this.fromApiJsonHelper = fromApiJsonHelper;
        this.transactionManager = transactionManager;
    }

    public PaymentHubDisbursementStatusData queryForLoan(final Long loanId) {
        final List<PaymentHubDisbursementPoll> pending = this.pollRepository.findByLoanIdAndStatusOrderByIdAsc(loanId,
                PaymentHubTransactionStatus.PENDING);
        if (pending.isEmpty()) {
            throw new LoanDisbursementRequestException("This loan does not have a pending Payment Hub disbursement to check.",
                    "paymenthub.disbursement.not.pending");
        }
        final String token = this.disbursementRequestService.paymentHubAccessToken();
        PaymentHubDisbursementStatusData latest = null;
        for (final PaymentHubDisbursementPoll poll : pending) {
            latest = queryOne(token, poll);
        }
        return latest;
    }

    private PaymentHubDisbursementStatusData queryOne(final String token, final PaymentHubDisbursementPoll poll) {
        final PaymentHubStatusQueryResult result = this.disbursementRequestService.queryTransactionStatus(token, poll.getRequestId(),
                poll.getTransactionId());
        final int httpStatus = result.getHttpStatus();
        if (httpStatus == 200 && result.getTransaction() != null) {
            return applyHubStatus(poll.getId(), result.getTransaction());
        }
        if (httpStatus == 404) {
            return applyMissingPayment(poll.getId(), result.getErrorMessage());
        }
        final String message = StringUtils.defaultIfBlank(result.getErrorMessage(),
                "The Payment Hub could not return the payment status. Please try again.");
        throw new LoanDisbursementRequestException(message, "paymenthub.disbursement.statusQueryFailed");
    }

    private PaymentHubDisbursementStatusData applyHubStatus(final Long pollId, final PaymentHubTransactionStatus hubStatus) {
        final TransactionTemplate template = new TransactionTemplate(this.transactionManager);
        return template.execute(status -> {
            final PaymentHubDisbursementPoll poll = this.pollRepository.findById(pollId).orElse(null);
            if (poll == null) {
                return null;
            }
            if (!PaymentHubTransactionStatus.PENDING.equals(poll.getStatus())) {
                return statusData(poll, "This payment status was already checked.");
            }
            if (hubStatus.isPending()) {
                poll.recordStillPending(hubStatus);
                this.pollRepository.save(poll);
                LOG.info("Payment Hub disbursement still pending loanId={}, requestId={}, reason={}", poll.getLoanId(),
                        poll.getRequestId(), hubStatus.getReason());
                return statusData(poll, "The payment is still pending at the Payment Hub.");
            }
            if (hubStatus.isSuccess()) {
                completeDisbursement(poll, hubStatus);
                return statusData(poll, "The payment succeeded and the loan disbursement was posted.");
            }
            if (hubStatus.isFailed() || hubStatus.isPendingError()) {
                failDisbursement(poll, hubStatus, fineractResultCode(hubStatus), hubStatus.getReason());
                return statusData(poll, hubStatus.isPendingError() ? "The Payment Hub stopped checking this payment."
                        : "The payment failed at the Payment Hub.");
            }
            LOG.warn("Payment Hub returned an unrecognised status {} for requestId={}", hubStatus.getStatus(), poll.getRequestId());
            return statusData(poll, "The Payment Hub returned a status CBS does not recognise.");
        });
    }

    private PaymentHubDisbursementStatusData applyMissingPayment(final Long pollId, final String errorMessage) {
        final TransactionTemplate template = new TransactionTemplate(this.transactionManager);
        return template.execute(status -> {
            final PaymentHubDisbursementPoll poll = this.pollRepository.findById(pollId).orElse(null);
            if (poll == null) {
                return null;
            }
            if (!PaymentHubTransactionStatus.PENDING.equals(poll.getStatus())) {
                return statusData(poll, "This payment status was already checked.");
            }
            final String reason = StringUtils.defaultIfBlank(errorMessage, "No payment matches this Payment Hub request.");
            failDisbursement(poll, null, "404", reason);
            return statusData(poll, "The Payment Hub has no record of this disbursement.");
        });
    }

    private static PaymentHubDisbursementStatusData statusData(final PaymentHubDisbursementPoll poll, final String message) {
        return new PaymentHubDisbursementStatusData(poll.getLoanId(), poll.getRequestId(), poll.getStatus(), poll.getReason(),
                poll.getTransactionRef(), message);
    }

    private void completeDisbursement(final PaymentHubDisbursementPoll poll, final PaymentHubTransactionStatus hubStatus) {
        final Loan loan = this.loanRepository.findById(poll.getLoanId()).orElse(null);
        if (loan == null) {
            poll.markTerminal(PaymentHubTransactionStatus.FAILED, hubStatus, "Loan was not found while applying the Payment Hub status.");
            this.pollRepository.save(poll);
            return;
        }
        if (trancheAlreadyDisbursed(loan, poll)) {
            poll.markTerminal(PaymentHubTransactionStatus.SUCCESS, hubStatus, hubStatus.getReason());
            this.pollRepository.save(poll);
            LOG.info("Payment Hub disbursement already posted in CBS loanId={}, requestId={}", loan.getId(), poll.getRequestId());
            return;
        }
        final LocalDate disbursementDate = disbursementDate(hubStatus, poll.getActualDisbursementDate());
        final JsonCommand command = jsonCommand(disbursementCommandJson(poll.getPaymentTypeId(), loan.getPrincpal().getAmount(),
                disbursementDate, "200", hubStatus.getTransactionRef()), loan.getId());
        this.loanWritePlatformService.updateDisbursement(loan.getId(), command);
        this.loanWritePlatformService.disburseLoan(loan.getId(), command, false, false);
        createResultNote(loan, "200", hubStatus.getTransactionRef(), hubStatus.getReason());
        poll.markTerminal(PaymentHubTransactionStatus.SUCCESS, hubStatus, hubStatus.getReason());
        this.pollRepository.save(poll);
        LOG.info("Payment Hub disbursement completed from status query loanId={}, requestId={}, transactionRef={}", loan.getId(),
                poll.getRequestId(), hubStatus.getTransactionRef());
    }

    private void failDisbursement(final PaymentHubDisbursementPoll poll, final PaymentHubTransactionStatus hubStatus,
            final String resultCode, final String reason) {
        final Loan loan = this.loanRepository.findById(poll.getLoanId()).orElse(null);
        if (loan != null && !trancheAlreadyDisbursed(loan, poll)) {
            final LocalDate disbursementDate = poll.getActualDisbursementDate() != null ? poll.getActualDisbursementDate()
                    : DateUtils.getBusinessLocalDate();
            final BigDecimal amount = loan.getPrincpal().getAmount();
            final String commandJson = disbursementCommandJson(poll.getPaymentTypeId(), amount, disbursementDate, resultCode, null);
            this.loanWritePlatformService.updateDisbursement(loan.getId(), jsonCommand(commandJson, loan.getId()));
            createResultNote(loan, resultCode, hubStatus == null ? null : hubStatus.getTransactionRef(), reason);
        }
        poll.markTerminal(hubStatus != null && hubStatus.isPendingError() ? PaymentHubTransactionStatus.PENDING_ERROR
                : PaymentHubTransactionStatus.FAILED, hubStatus, reason);
        this.pollRepository.save(poll);
        LOG.info("Payment Hub disbursement finished unsuccessfully loanId={}, requestId={}, status={}", poll.getLoanId(),
                poll.getRequestId(), poll.getStatus());
    }

    private void createResultNote(final Loan loan, final String resultCode, final String transactionReference, final String reason) {
        final String note = LoanDisbursementIntegrationApiResource.bankDisbursementResultNote(resultCode, transactionReference, reason);
        this.noteRepository.save(Note.loanNote(loan, note));
    }

    private JsonCommand jsonCommand(final String json, final Long loanId) {
        final JsonElement parsed = this.fromApiJsonHelper.parse(json);
        return JsonCommand.from(json, parsed, this.fromApiJsonHelper, "LOAN", loanId, null, null, null, loanId, null, null, null, null, null,
                null);
    }

    private static boolean trancheAlreadyDisbursed(final Loan loan, final PaymentHubDisbursementPoll poll) {
        if (poll.getLoanDisbursementDetailId() != null) {
            for (final LoanDisbursementDetails detail : loan.getDisbursementDetails()) {
                if (poll.getLoanDisbursementDetailId().equals(detail.getId())) {
                    return detail.actualDisbursementDate() != null;
                }
            }
        }
        return loan.isDisbursed();
    }

    static String fineractResultCode(final PaymentHubTransactionStatus hubStatus) {
        if (hubStatus == null) {
            return "500";
        }
        if (hubStatus.isSuccess()) {
            return "200";
        }
        if (hubStatus.isPendingError()) {
            return "5001";
        }
        if (hubStatus.getStatusCode() != null) {
            return String.valueOf(hubStatus.getStatusCode());
        }
        return "500";
    }

    static LocalDate disbursementDate(final PaymentHubTransactionStatus hubStatus, final LocalDate requestedDate) {
        final LocalDate reported = hubStatus == null ? null : PaymentHubTransactionStatus.parseTransactionDate(hubStatus.getTransactionDate());
        if (reported != null) {
            return reported;
        }
        if (requestedDate != null) {
            return requestedDate;
        }
        return DateUtils.getBusinessLocalDate();
    }

    static String disbursementCommandJson(final Long paymentTypeId, final BigDecimal amount, final LocalDate disbursementDate,
            final String resultCode, final String receiptNumber) {
        final JsonObject body = new JsonObject();
        if (paymentTypeId != null) {
            body.addProperty("paymentTypeId", paymentTypeId);
        }
        if (amount != null) {
            body.addProperty("transactionAmount", amount);
        }
        body.addProperty("actualDisbursementDate", disbursementDate.toString());
        body.addProperty("locale", "en");
        body.addProperty("dateFormat", "yyyy-MM-dd");
        body.addProperty("resultCode", resultCode);
        if (StringUtils.isNotBlank(receiptNumber)) {
            body.addProperty("receiptNumber", receiptNumber);
        }
        return body.toString();
    }
}
