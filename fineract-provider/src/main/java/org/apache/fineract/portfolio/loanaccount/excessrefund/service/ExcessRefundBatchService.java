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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.loanaccount.excessrefund.domain.LoanExcessRefund;
import org.apache.fineract.portfolio.loanaccount.excessrefund.domain.LoanExcessRefundBatch;
import org.apache.fineract.portfolio.loanaccount.excessrefund.domain.LoanExcessRefundStatus;
import org.apache.fineract.portfolio.loanaccount.excessrefund.repository.LoanExcessRefundBatchRepository;
import org.apache.fineract.portfolio.loanaccount.excessrefund.repository.LoanExcessRefundRepository;
import org.apache.fineract.useradministration.domain.AppUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExcessRefundBatchService {

    private final PlatformSecurityContext context;
    private final LoanExcessRefundBatchRepository batchRepository;
    private final LoanExcessRefundRepository refundRepository;
    private final ExcessRefundWritePlatformService writePlatformService;
    private final FromJsonHelper fromJsonHelper;

    @Transactional
    public CommandProcessingResult createBatch(final JsonCommand command) {
        final AppUser user = this.context.authenticatedUser();
        user.validateHasPermissionTo("CREATE_EXCESS_REFUND_BATCH");
        final Long officeId = command.longValueOfParameterNamed(OFFICE_ID_PARAM);
        final JsonElement parsed = command.parsedJson();
        if (parsed == null || !parsed.isJsonObject() || !parsed.getAsJsonObject().has(ITEMS_PARAM)) {
            throw new GeneralPlatformDomainRuleException("error.msg.excess.refund.batch.line.invalid",
                    "Batch items are required.");
        }
        final JsonArray items = parsed.getAsJsonObject().getAsJsonArray(ITEMS_PARAM);
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

        int failed = 0;
        for (final JsonElement itemElement : items) {
            final JsonObject item = itemElement.getAsJsonObject().deepCopy();
            try {
                if (!item.has("locale")) {
                    item.addProperty("locale", "en");
                }
                if (!item.has("dateFormat")) {
                    item.addProperty("dateFormat", "dd MMMM yyyy");
                }
                final JsonCommand itemCommand = JsonCommand.from(item.toString(), item, this.fromJsonHelper, "EXCESS_REFUND", null, null,
                        null, null, null, null, null, null, null, null, null);
                final CommandProcessingResult created = this.writePlatformService.create(itemCommand);
                final LoanExcessRefund refund = this.refundRepository.findById(created.getEntityId()).orElseThrow();
                refund.setBatch(batch);
                refund.setUpdatedAt(DateUtils.getLocalDateTimeOfTenant());
                this.refundRepository.save(refund);
            } catch (RuntimeException ex) {
                failed++;
            }
        }
        batch.setFailedCount(failed);
        batch.setSuccessCount(batch.getTotalCount() - failed);
        batch.setUpdatedAt(DateUtils.getLocalDateTimeOfTenant());
        this.batchRepository.saveAndFlush(batch);

        final Map<String, Object> changes = new HashMap<>();
        changes.put("totalCount", batch.getTotalCount());
        changes.put("successCount", batch.getSuccessCount());
        changes.put("failedCount", batch.getFailedCount());
        return new CommandProcessingResultBuilder().withEntityId(batch.getId()).withOfficeId(officeId).with(changes).build();
    }

    @Transactional
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
        if (batch.getSubmittedBy() != null && batch.getSubmittedBy().getId().equals(user.getId())) {
            throw new GeneralPlatformDomainRuleException("error.msg.excess.refund.approver.same.as.maker",
                    "Maker and checker must be different users.");
        }
        final List<LoanExcessRefund> lines = this.refundRepository.findByBatchId(batch.getId());
        int approved = 0;
        int failed = 0;
        for (final LoanExcessRefund line : lines) {
            if (line.getStatus() != LoanExcessRefundStatus.PENDING_APPROVAL) {
                continue;
            }
            try {
                line.setStatus(LoanExcessRefundStatus.APPROVED);
                line.setApprovedBy(user);
                line.setApprovedAt(DateUtils.getLocalDateTimeOfTenant());
                line.setUpdatedAt(DateUtils.getLocalDateTimeOfTenant());
                this.refundRepository.save(line);
                approved++;
            } catch (RuntimeException ex) {
                failed++;
            }
        }
        batch.setStatusEnum(STATUS_APPROVED);
        batch.setApprover(user);
        batch.setApprovedAt(DateUtils.getLocalDateTimeOfTenant());
        batch.setApprovalNote(command.stringValueOfParameterNamedAllowingNull(NOTE_PARAM));
        batch.setSuccessCount(approved);
        batch.setFailedCount(failed);
        batch.setUpdatedAt(DateUtils.getLocalDateTimeOfTenant());
        this.batchRepository.saveAndFlush(batch);
        final Map<String, Object> changes = new HashMap<>();
        changes.put("approvedCount", approved);
        changes.put("failedCount", failed);
        return new CommandProcessingResultBuilder().withEntityId(batch.getId()).with(changes).build();
    }

    @Transactional
    public CommandProcessingResult rejectBatch(final JsonCommand command) {
        final AppUser user = this.context.authenticatedUser();
        user.validateHasPermissionTo("REJECT_EXCESS_REFUND_BATCH");
        final LoanExcessRefundBatch batch = this.batchRepository.findById(command.entityId())
                .orElseThrow(() -> new GeneralPlatformDomainRuleException("error.msg.excess.refund.batch.not.found",
                        "Excess refund batch not found: " + command.entityId()));
        batch.setStatusEnum(STATUS_REJECTED);
        batch.setApprover(user);
        batch.setApprovedAt(DateUtils.getLocalDateTimeOfTenant());
        batch.setApprovalNote(command.stringValueOfParameterNamedAllowingNull(NOTE_PARAM));
        batch.setUpdatedAt(DateUtils.getLocalDateTimeOfTenant());
        this.batchRepository.saveAndFlush(batch);
        for (final LoanExcessRefund line : this.refundRepository.findByBatchId(batch.getId())) {
            if (line.getStatus() == LoanExcessRefundStatus.PENDING_APPROVAL) {
                line.setStatus(LoanExcessRefundStatus.REJECTED);
                line.setRejectedBy(user);
                line.setRejectedAt(DateUtils.getLocalDateTimeOfTenant());
                line.setRejectionNote(batch.getApprovalNote());
                line.setUpdatedAt(DateUtils.getLocalDateTimeOfTenant());
                this.refundRepository.save(line);
            }
        }
        return new CommandProcessingResultBuilder().withEntityId(batch.getId()).build();
    }
}
