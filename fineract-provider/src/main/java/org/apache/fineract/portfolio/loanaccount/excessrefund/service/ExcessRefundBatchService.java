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

import static org.apache.fineract.portfolio.loanaccount.excessrefund.data.ExcessRefundApiConstants.ITEMS_PARAM;
import static org.apache.fineract.portfolio.loanaccount.excessrefund.data.ExcessRefundApiConstants.NOTE_PARAM;
import static org.apache.fineract.portfolio.loanaccount.excessrefund.data.ExcessRefundApiConstants.OFFICE_ID_PARAM;
import static org.apache.fineract.portfolio.loanaccount.excessrefund.domain.LoanExcessRefundBatch.STATUS_APPROVED;
import static org.apache.fineract.portfolio.loanaccount.excessrefund.domain.LoanExcessRefundBatch.STATUS_PENDING_APPROVAL;
import static org.apache.fineract.portfolio.loanaccount.excessrefund.domain.LoanExcessRefundBatch.STATUS_REJECTED;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.exception.AbstractPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.service.OfficeHierarchyService;
import org.apache.fineract.portfolio.loanaccount.excessrefund.domain.LoanExcessRefund;
import org.apache.fineract.portfolio.loanaccount.excessrefund.domain.LoanExcessRefundBatch;
import org.apache.fineract.portfolio.loanaccount.excessrefund.domain.LoanExcessRefundStatus;
import org.apache.fineract.portfolio.loanaccount.excessrefund.repository.LoanExcessRefundBatchRepository;
import org.apache.fineract.portfolio.loanaccount.excessrefund.repository.LoanExcessRefundRepository;
import org.apache.fineract.useradministration.domain.AppUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class ExcessRefundBatchService {

    private final PlatformSecurityContext context;
    private final LoanExcessRefundBatchRepository batchRepository;
    private final LoanExcessRefundRepository refundRepository;
    private final ExcessRefundWritePlatformService writePlatformService;
    private final FromJsonHelper fromJsonHelper;
    private final OfficeHierarchyService officeHierarchyService;
    private final PlatformTransactionManager transactionManager;

    public CommandProcessingResult createBatch(final JsonCommand command) {
        final AppUser user = this.context.authenticatedUser();
        user.validateHasPermissionTo("CREATE_EXCESS_REFUND_BATCH");
        final Long officeId = command.longValueOfParameterNamed(OFFICE_ID_PARAM);
        if (!this.officeHierarchyService.validateUserAccessToOffice(user, officeId)) {
            throw new GeneralPlatformDomainRuleException("error.msg.excess.refund.office.denied",
                    "User does not have access to the selected office for this excess refund batch.");
        }
        final JsonElement parsed = command.parsedJson();
        if (parsed == null || !parsed.isJsonObject() || !parsed.getAsJsonObject().has(ITEMS_PARAM)) {
            throw new GeneralPlatformDomainRuleException("error.msg.excess.refund.batch.line.invalid", "Batch items are required.");
        }
        final JsonArray items = parsed.getAsJsonObject().getAsJsonArray(ITEMS_PARAM);
        final TransactionTemplate requiresNew = newRequiresNewTemplate();

        final Long batchId = requiresNew.execute(status -> {
            final LocalDateTime now = DateUtils.getLocalDateTimeOfTenant();
            final LoanExcessRefundBatch batch = new LoanExcessRefundBatch();
            batch.setOfficeId(officeId);
            batch.setSubmittedBy(user);
            batch.setStatusEnum(STATUS_PENDING_APPROVAL);
            batch.setTotalCount(items.size());
            batch.setSuccessCount(0);
            batch.setFailedCount(0);
            batch.setSubmissionNote(command.stringValueOfParameterNamedAllowingNull(NOTE_PARAM));
            batch.setCreatedAt(now);
            batch.setUpdatedAt(now);
            this.batchRepository.saveAndFlush(batch);
            return batch.getId();
        });

        final List<String> lineErrors = new ArrayList<>();
        int failed = 0;
        int success = 0;
        for (final JsonElement itemElement : items) {
            final JsonObject item = itemElement.getAsJsonObject().deepCopy();
            try {
                if (!item.has("locale")) {
                    item.addProperty("locale", "en");
                }
                if (!item.has("dateFormat")) {
                    item.addProperty("dateFormat", "dd MMMM yyyy");
                }
                requiresNew.execute(status -> {
                    final JsonCommand itemCommand = JsonCommand.from(item.toString(), item, this.fromJsonHelper, "EXCESS_REFUND", null,
                            null, null, null, null, null, null, null, null, null, null);
                    final CommandProcessingResult created = this.writePlatformService.create(itemCommand);
                    final LoanExcessRefund refund = this.refundRepository.findById(created.getEntityId()).orElseThrow();
                    final LoanExcessRefundBatch batch = this.batchRepository.findById(batchId).orElseThrow();
                    refund.setBatch(batch);
                    refund.setUpdatedAt(DateUtils.getLocalDateTimeOfTenant());
                    this.refundRepository.saveAndFlush(refund);
                    return created.getEntityId();
                });
                success++;
            } catch (RuntimeException ex) {
                failed++;
                lineErrors.add(summarizeLineError(item, ex));
            }
        }

        final int finalSuccess = success;
        final int finalFailed = failed;
        requiresNew.execute(status -> {
            final LoanExcessRefundBatch batch = this.batchRepository.findById(batchId).orElseThrow();
            batch.setFailedCount(finalFailed);
            batch.setSuccessCount(finalSuccess);
            if (!lineErrors.isEmpty()) {
                final String existing = StringUtils.defaultString(batch.getSubmissionNote());
                batch.setSubmissionNote(StringUtils.abbreviate(
                        existing + (existing.isEmpty() ? "" : "\n") + "Line errors:\n" + String.join("\n", lineErrors), 4000));
            }
            batch.setUpdatedAt(DateUtils.getLocalDateTimeOfTenant());
            this.batchRepository.saveAndFlush(batch);
            return null;
        });

        final Map<String, Object> changes = new HashMap<>();
        changes.put("totalCount", items.size());
        changes.put("successCount", success);
        changes.put("failedCount", failed);
        changes.put("lineErrors", lineErrors);
        return new CommandProcessingResultBuilder().withEntityId(batchId).withOfficeId(officeId).with(changes).build();
    }

    public CommandProcessingResult approveBatch(final JsonCommand command) {
        final AppUser user = this.context.authenticatedUser();
        user.validateHasPermissionTo("APPROVE_EXCESS_REFUND_BATCH");
        final LoanExcessRefundBatch batch = this.batchRepository.findById(command.entityId())
                .orElseThrow(() -> new GeneralPlatformDomainRuleException("error.msg.excess.refund.batch.not.found",
                        "Excess refund batch not found: " + command.entityId()));
        if (batch.getStatusEnum() != STATUS_PENDING_APPROVAL) {
            throw new GeneralPlatformDomainRuleException("error.msg.excess.refund.invalid.status",
                    "Only pending batches can be approved.");
        }
        assertBatchMakerChecker(batch, user);
        if (!this.officeHierarchyService.validateUserAccessToOffice(user, batch.getOfficeId())) {
            throw new GeneralPlatformDomainRuleException("error.msg.excess.refund.office.denied",
                    "User does not have access to the batch office.");
        }

        final TransactionTemplate requiresNew = newRequiresNewTemplate();
        final List<LoanExcessRefund> lines = this.refundRepository.findByBatchId(batch.getId());
        int approved = 0;
        int failed = 0;
        final List<String> lineErrors = new ArrayList<>();
        for (final LoanExcessRefund line : lines) {
            if (line.getStatus() != LoanExcessRefundStatus.PENDING_APPROVAL) {
                continue;
            }
            try {
                requiresNew.execute(status -> {
                    final JsonCommand approveCommand = JsonCommand.fromExistingCommand(command.commandId(),
                            StringUtils.defaultIfBlank(command.json(), "{}"), command.parsedJson(), this.fromJsonHelper, "EXCESS_REFUND",
                            line.getId(), null, null, null, null, null, null, null, null, null, null);
                    this.writePlatformService.approve(approveCommand);
                    return null;
                });
                approved++;
            } catch (RuntimeException ex) {
                failed++;
                lineErrors.add("refundId=" + line.getId() + ": " + summarizeException(ex));
            }
        }

        final int finalApproved = approved;
        final int finalFailed = failed;
        requiresNew.execute(status -> {
            final LoanExcessRefundBatch managed = this.batchRepository.findById(batch.getId()).orElseThrow();
            managed.setStatusEnum(STATUS_APPROVED);
            managed.setApprover(user);
            managed.setApprovedAt(DateUtils.getLocalDateTimeOfTenant());
            String note = command.stringValueOfParameterNamedAllowingNull(NOTE_PARAM);
            if (!lineErrors.isEmpty()) {
                note = StringUtils.abbreviate(StringUtils.defaultString(note) + "\nLine errors:\n" + String.join("\n", lineErrors), 4000);
            }
            managed.setApprovalNote(note);
            managed.setSuccessCount(finalApproved);
            managed.setFailedCount(finalFailed);
            managed.setUpdatedAt(DateUtils.getLocalDateTimeOfTenant());
            this.batchRepository.saveAndFlush(managed);
            return null;
        });

        final Map<String, Object> changes = new HashMap<>();
        changes.put("approvedCount", approved);
        changes.put("failedCount", failed);
        changes.put("lineErrors", lineErrors);
        return new CommandProcessingResultBuilder().withEntityId(batch.getId()).with(changes).build();
    }

    public CommandProcessingResult rejectBatch(final JsonCommand command) {
        final AppUser user = this.context.authenticatedUser();
        user.validateHasPermissionTo("REJECT_EXCESS_REFUND_BATCH");
        final LoanExcessRefundBatch batch = this.batchRepository.findById(command.entityId())
                .orElseThrow(() -> new GeneralPlatformDomainRuleException("error.msg.excess.refund.batch.not.found",
                        "Excess refund batch not found: " + command.entityId()));
        if (batch.getStatusEnum() != STATUS_PENDING_APPROVAL) {
            throw new GeneralPlatformDomainRuleException("error.msg.excess.refund.invalid.status",
                    "Only pending batches can be rejected.");
        }
        assertBatchMakerChecker(batch, user);
        if (!this.officeHierarchyService.validateUserAccessToOffice(user, batch.getOfficeId())) {
            throw new GeneralPlatformDomainRuleException("error.msg.excess.refund.office.denied",
                    "User does not have access to the batch office.");
        }

        final TransactionTemplate requiresNew = newRequiresNewTemplate();
        final String rejectionNote = command.stringValueOfParameterNamedAllowingNull(NOTE_PARAM);
        for (final LoanExcessRefund line : this.refundRepository.findByBatchId(batch.getId())) {
            if (line.getStatus() != LoanExcessRefundStatus.PENDING_APPROVAL) {
                continue;
            }
            requiresNew.execute(status -> {
                final JsonObject body = new JsonObject();
                if (StringUtils.isNotBlank(rejectionNote)) {
                    body.addProperty("note", rejectionNote);
                }
                final JsonCommand rejectCommand = JsonCommand.from(body.toString(), body, this.fromJsonHelper, "EXCESS_REFUND",
                        line.getId(), null, null, null, null, null, null, null, null, null, null);
                this.writePlatformService.reject(rejectCommand);
                return null;
            });
        }

        requiresNew.execute(status -> {
            final LoanExcessRefundBatch managed = this.batchRepository.findById(batch.getId()).orElseThrow();
            managed.setStatusEnum(STATUS_REJECTED);
            managed.setApprover(user);
            managed.setApprovedAt(DateUtils.getLocalDateTimeOfTenant());
            managed.setApprovalNote(rejectionNote);
            managed.setUpdatedAt(DateUtils.getLocalDateTimeOfTenant());
            this.batchRepository.saveAndFlush(managed);
            return null;
        });
        return new CommandProcessingResultBuilder().withEntityId(batch.getId()).build();
    }

    private TransactionTemplate newRequiresNewTemplate() {
        final TransactionTemplate template = new TransactionTemplate(this.transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    private void assertBatchMakerChecker(final LoanExcessRefundBatch batch, final AppUser user) {
        if (batch.getSubmittedBy() != null && batch.getSubmittedBy().getId().equals(user.getId())) {
            throw new GeneralPlatformDomainRuleException("error.msg.excess.refund.approver.same.as.maker",
                    "Maker and checker must be different users.");
        }
    }

    private static String summarizeLineError(final JsonObject item, final RuntimeException ex) {
        final String loanId = item.has("loanId") ? item.get("loanId").toString() : "?";
        return "loanId=" + loanId + ": " + summarizeException(ex);
    }

    private static String summarizeException(final RuntimeException ex) {
        if (ex instanceof AbstractPlatformDomainRuleException) {
            return ((AbstractPlatformDomainRuleException) ex).getDefaultUserMessage();
        }
        return StringUtils.defaultIfBlank(ex.getMessage(), ex.getClass().getSimpleName());
    }
}
