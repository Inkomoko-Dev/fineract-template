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
package org.apache.fineract.portfolio.loanclassification.service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.codes.domain.CodeValue;
import org.apache.fineract.infrastructure.codes.domain.CodeValueRepository;
import org.apache.fineract.infrastructure.codes.exception.CodeValueNotFoundException;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.apache.fineract.portfolio.loanclassification.api.LoanClassificationApiConstants;
import org.apache.fineract.portfolio.loanclassification.data.LoanClassificationArrearsBand;
import org.apache.fineract.portfolio.loanclassification.data.LoanClassificationCodes;
import org.apache.fineract.portfolio.loanclassification.data.LoanClassificationOutcome;
import org.apache.fineract.portfolio.loanclassification.domain.LoanClassificationAudit;
import org.apache.fineract.portfolio.loanclassification.domain.LoanClassificationAuditRepository;
import org.apache.fineract.portfolio.loanclassification.domain.LoanClassificationCountryConfig;
import org.apache.fineract.portfolio.loanclassification.domain.LoanClassificationCountryConfigRepository;
import org.apache.fineract.portfolio.loanclassification.domain.LoanClassificationRecord;
import org.apache.fineract.portfolio.loanclassification.domain.LoanClassificationRecordRepository;
import org.apache.fineract.portfolio.loanclassification.domain.LoanClassificationThreshold;
import org.apache.fineract.portfolio.loanclassification.exception.LoanClassificationCountryConfigNotFoundException;
import org.apache.fineract.portfolio.loanclassification.serialization.LoanClassificationCommandFromApiJsonDeserializer;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class LoanClassificationWritePlatformServiceImpl implements LoanClassificationWritePlatformService {

    private final PlatformSecurityContext context;
    private final LoanClassificationCommandFromApiJsonDeserializer fromApiJsonDeserializer;
    private final LoanClassificationCountryConfigRepository countryConfigRepository;
    private final LoanClassificationRecordRepository recordRepository;
    private final LoanClassificationAuditRepository auditRepository;
    private final CodeValueRepository codeValueRepository;
    private final LoanRepositoryWrapper loanRepositoryWrapper;
    private final LoanClassificationCandidateAssembler candidateAssembler;

    @Override
    @Transactional
    public CommandProcessingResult createCountryConfig(final JsonCommand command) {
        this.fromApiJsonDeserializer.validateForCreate(command.json());
        final Long countryId = command.longValueOfParameterNamed(LoanClassificationApiConstants.COUNTRY_ID_PARAM);
        final CodeValue country = this.codeValueRepository.findByCodeNameAndId("COUNTRY", countryId);
        if (country == null) {
            throw new CodeValueNotFoundException("COUNTRY", countryId);
        }
        if (this.countryConfigRepository.existsByCountryCvId(countryId)) {
            throw new GeneralPlatformDomainRuleException("error.msg.loan.classification.country.already.configured",
                    "Loan classification thresholds already exist for this country", countryId);
        }
        final OffsetDateTime now = DateUtils.getOffsetDateTimeOfTenant();
        final Long userId = currentUserId();
        final LoanClassificationCountryConfig config = LoanClassificationCountryConfig.create(countryId, userId, now);
        config.replaceThresholds(this.fromApiJsonDeserializer.extractThresholds(command.json()), userId, now);
        this.countryConfigRepository.saveAndFlush(config);
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(config.getId()).build();
    }

    @Override
    @Transactional
    public CommandProcessingResult updateCountryConfig(final Long configId, final JsonCommand command) {
        this.fromApiJsonDeserializer.validateForUpdate(command.json());
        final LoanClassificationCountryConfig config = this.countryConfigRepository.findById(configId)
                .orElseThrow(() -> new LoanClassificationCountryConfigNotFoundException(configId));
        final OffsetDateTime now = DateUtils.getOffsetDateTimeOfTenant();
        config.replaceThresholds(this.fromApiJsonDeserializer.extractThresholds(command.json()), currentUserId(), now);
        this.countryConfigRepository.saveAndFlush(config);
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(config.getId()).build();
    }

    @Override
    @Transactional
    public CommandProcessingResult deleteCountryConfig(final Long configId) {
        final LoanClassificationCountryConfig config = this.countryConfigRepository.findById(configId)
                .orElseThrow(() -> new LoanClassificationCountryConfigNotFoundException(configId));
        try {
            this.countryConfigRepository.delete(config);
            this.countryConfigRepository.flush();
        } catch (final DataIntegrityViolationException ex) {
            throw new GeneralPlatformDomainRuleException("error.msg.loan.classification.country.config.cannot.delete",
                    "Unable to delete loan classification country configuration", configId);
        }
        return new CommandProcessingResultBuilder().withEntityId(configId).build();
    }

    @Override
    @Transactional
    public CommandProcessingResult overrideClassification(final Long loanId, final JsonCommand command) {
        this.context.authenticatedUser().validateHasPermissionTo("OVERRIDE_LOANCLASSIFICATION");
        this.fromApiJsonDeserializer.validateOverride(command.json());
        this.loanRepositoryWrapper.findOneWithNotFoundDetection(loanId);
        final Integer classification = command.integerValueSansLocaleOfParameterNamed(LoanClassificationApiConstants.CLASSIFICATION_PARAM);
        if (!LoanClassificationCodes.isValid(classification)) {
            throw new GeneralPlatformDomainRuleException("error.msg.loan.classification.invalid",
                    "Classification must be between 1 and 6 and cannot be 0 or null", classification);
        }
        final String reason = command.stringValueOfParameterNamed(LoanClassificationApiConstants.REASON_PARAM);
        final OffsetDateTime now = DateUtils.getOffsetDateTimeOfTenant();
        final Long userId = currentUserId();
        final LoanClassificationRecord record = this.recordRepository.findByLoanId(loanId)
                .orElseGet(() -> LoanClassificationRecord.create(loanId));
        final Integer previous = record.getClassificationCode();
        final String previousStatus = record.getStatus();
        record.applyManualOverride(classification, reason, userId, now);
        this.recordRepository.saveAndFlush(record);
        this.auditRepository.saveAndFlush(LoanClassificationAudit.entry(loanId, previous, classification, previousStatus,
                record.getStatus(), LoanClassificationOutcome.SOURCE_MANUAL, record.getDaysInArrears(), record.getCountryCvId(), reason,
                null, userId, now));
        log.info("Manual loan classification override loanId={} classification={} by userId={}", loanId, classification, userId);
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withLoanId(loanId).withEntityId(record.getId())
                .build();
    }

    @Override
    @Transactional
    public void classifyLoan(final Long loanId, final String source) {
        final LoanClassificationCandidateAssembler.Candidate candidate = this.candidateAssembler.load(loanId);
        applyComputed(candidate, source, currentUserIdOrNull(), DateUtils.getOffsetDateTimeOfTenant(), false);
    }

    @Override
    @Transactional
    public void classifyWrittenOffLoan(final Long loanId) {
        final Loan loan = this.loanRepositoryWrapper.findOneWithNotFoundDetection(loanId);
        final LoanClassificationCandidateAssembler.Candidate candidate = this.candidateAssembler.load(loan.getId());
        applyComputed(new LoanClassificationCandidateAssembler.Candidate(candidate.getLoanId(), true, candidate.getDaysInArrears(),
                candidate.getCountryCvId(), candidate.getTotalOverdue()), LoanClassificationOutcome.SOURCE_WRITE_OFF,
                currentUserIdOrNull(), DateUtils.getOffsetDateTimeOfTenant(), true);
    }

    LoanClassificationOutcome applyComputed(final LoanClassificationCandidateAssembler.Candidate candidate, final String source,
            final Long userId, final OffsetDateTime now, final boolean ignoreActiveOverride) {
        return applyComputed(candidate, source, userId, now, ignoreActiveOverride, null);
    }

    @Transactional
    public LoanClassificationOutcome applyComputed(final LoanClassificationCandidateAssembler.Candidate candidate, final String source,
            final Long userId, final OffsetDateTime now, final boolean ignoreActiveOverride,
            final Map<Long, List<LoanClassificationArrearsBand>> bandCache) {
        final LoanClassificationRecord record = this.recordRepository.findByLoanId(candidate.getLoanId())
                .orElseGet(() -> LoanClassificationRecord.create(candidate.getLoanId()));
        if (!ignoreActiveOverride && record.isOverrideActive()) {
            log.info("Skipping auto classification for loan {} because a manual override is active until the next scheduled run",
                    candidate.getLoanId());
            return null;
        }
        final List<LoanClassificationArrearsBand> bands = bandsFor(candidate.getCountryCvId(), bandCache);
        final LoanClassificationOutcome outcome = LoanClassificationCalculator.classify(candidate.isWrittenOff(),
                candidate.getDaysInArrears(), bands);
        final Integer previous = record.getClassificationCode();
        final String previousStatus = record.getStatus();
        record.apply(outcome, candidate.getDaysInArrears(), candidate.getCountryCvId(), userId, now);
        this.recordRepository.saveAndFlush(record);
        this.auditRepository.saveAndFlush(LoanClassificationAudit.entry(candidate.getLoanId(), previous, record.getClassificationCode(),
                previousStatus, record.getStatus(), source, candidate.getDaysInArrears(), candidate.getCountryCvId(), null,
                record.getErrorMessage(), userId, now));
        if (record.isExcludedFromDownstream()) {
            log.error("Loan {} cannot be classified and is excluded from downstream processing: {}", candidate.getLoanId(),
                    record.getErrorMessage());
        }
        return outcome;
    }

    private List<LoanClassificationArrearsBand> bandsFor(final Long countryCvId,
            final Map<Long, List<LoanClassificationArrearsBand>> bandCache) {
        if (countryCvId == null) {
            return new ArrayList<>();
        }
        if (bandCache != null) {
            return bandCache.computeIfAbsent(countryCvId, this::loadBands);
        }
        return loadBands(countryCvId);
    }

    private List<LoanClassificationArrearsBand> loadBands(final Long countryCvId) {
        return this.countryConfigRepository.findByCountryCvId(countryCvId)
                .map(config -> config.getThresholds().stream().map(LoanClassificationThreshold::toBand).collect(Collectors.toList()))
                .orElseGet(ArrayList::new);
    }

    private Long currentUserId() {
        return this.context.authenticatedUser().getId();
    }

    private Long currentUserIdOrNull() {
        try {
            return this.context.authenticatedUser().getId();
        } catch (final Exception ex) {
            return null;
        }
    }
}
