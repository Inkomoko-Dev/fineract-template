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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import javax.persistence.PersistenceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.codes.data.CodeValueData;
import org.apache.fineract.infrastructure.configuration.data.GlobalConfigurationPropertyData;
import org.apache.fineract.infrastructure.configuration.service.ConfigurationReadPlatformService;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.exception.PlatformDataIntegrityException;
import org.apache.fineract.infrastructure.documentmanagement.data.DocumentData;
import org.apache.fineract.infrastructure.documentmanagement.service.DocumentReadPlatformService;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.staff.domain.Staff;
import org.apache.fineract.portfolio.businessevent.domain.loan.LoanDecisionAcceptedEvent;
import org.apache.fineract.portfolio.businessevent.domain.loan.transaction.LoanDecisionRejectEvent;
import org.apache.fineract.portfolio.businessevent.service.BusinessEventNotifierService;
import org.apache.fineract.portfolio.client.data.ClientOtherInfoData;
import org.apache.fineract.portfolio.client.exception.ClientOtherInfoNotFoundException;
import org.apache.fineract.portfolio.client.service.ClientOtherInfoReadPlatformService;
import org.apache.fineract.portfolio.common.domain.PeriodFrequencyType;
import org.apache.fineract.portfolio.fund.domain.Fund;
import org.apache.fineract.portfolio.loanaccount.api.LoanApiConstants;
import org.apache.fineract.portfolio.loanaccount.api.LoanApprovalMatrixConstants;
import org.apache.fineract.portfolio.loanaccount.data.LoanCashFlowReport;
import org.apache.fineract.portfolio.loanaccount.data.LoanFinancialRatioData;
import org.apache.fineract.portfolio.loanaccount.data.ScheduleGeneratorDTO;
import org.apache.fineract.portfolio.loanaccount.domain.IcReviewLevelConfig;
import org.apache.fineract.portfolio.loanaccount.domain.IcReviewLevelConfigRepository;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanApprovalMatrix;
import org.apache.fineract.portfolio.loanaccount.domain.LoanApprovalMatrixLevel;
import org.apache.fineract.portfolio.loanaccount.domain.LoanApprovalMatrixLevelRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanApprovalMatrixRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCollateralManagementRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanDecision;
import org.apache.fineract.portfolio.loanaccount.domain.LoanDecisionLevel;
import org.apache.fineract.portfolio.loanaccount.domain.LoanDecisionLevelRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanDecisionRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanDecisionState;
import org.apache.fineract.portfolio.loanaccount.domain.LoanDueDiligenceInfoRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.domain.MetropolCrbIdentityReport;
import org.apache.fineract.portfolio.loanaccount.domain.MetropolCrbIdentityVerificationRepository;
import org.apache.fineract.portfolio.loanaccount.domain.TransunionCrbHeader;
import org.apache.fineract.portfolio.loanaccount.domain.TransunionCrbHeaderRepository;
import org.apache.fineract.portfolio.loanaccount.exception.LoanDueDiligenceException;
import org.apache.fineract.portfolio.loanaccount.serialization.LoanDecisionTransitionApiJsonValidator;
import org.apache.fineract.portfolio.note.domain.Note;
import org.apache.fineract.portfolio.note.domain.NoteRepository;
import org.apache.fineract.useradministration.domain.AppUser;
import org.apache.fineract.useradministration.domain.AppUserRepository;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

@Service
@Slf4j
@RequiredArgsConstructor
public class LoanDecisionWritePlatformServiceJpaRepositoryImpl implements LoanApplicationDecisionWritePlatformService {

    private final PlatformSecurityContext context;
    private final AppUserRepository appUserRepository;
    private final LoanDecisionTransitionApiJsonValidator loanDecisionTransitionApiJsonValidator;
    private final LoanRepositoryWrapper loanRepositoryWrapper;
    private final LoanDecisionRepository loanDecisionRepository;
    private final ConfigurationReadPlatformService configurationReadPlatformService;
    private final LoanReadPlatformService loanReadPlatformService;
    private final LoanDecisionAssembler loanDecisionAssembler;
    private final LoanDueDiligenceInfoRepository loanDueDiligenceInfoRepository;
    private final NoteRepository noteRepository;
    private final LoanApprovalMatrixRepository loanApprovalMatrixRepository;
    private final LoanCollateralManagementRepository loanCollateralManagementRepository;
    private final LoanDecisionStateUtilService loanDecisionStateUtilService;
    private final DocumentReadPlatformService documentReadPlatformService;
    private final MetropolCrbIdentityVerificationRepository metropolCrbIdentityVerificationRepository;
    private final TransunionCrbHeaderRepository transunionCrbHeaderRepository;
    private final LoanUtilService loanUtilService;
    private final ClientOtherInfoReadPlatformService clientOtherInfoReadPlatformService;
    private final KivaLoanService kivaLoanService;
    private final BusinessEventNotifierService businessEventNotifierService;
    private final DynamicIcReviewLevelHelper dynamicIcReviewLevelHelper;
    private final IcReviewLevelConfigRepository icReviewLevelConfigRepository;
    private final LoanDecisionLevelRepository loanDecisionLevelRepository;
    private final LoanApprovalMatrixLevelRepository loanApprovalMatrixLevelRepository;

    @Override
    public CommandProcessingResult createLoanApprovalMatrix(JsonCommand command) {

        Boolean isExtendLoanLifeCycleConfig = loanDecisionStateUtilService.getExtendLoanLifeCycleConfig().isEnabled();
        if (!isExtendLoanLifeCycleConfig) {
            throw new GeneralPlatformDomainRuleException("error.msg.Add-More-Stages-To-A-Loan-Life-Cycle.is.not.set",
                    "Add-More-Stages-To-A-Loan-Life-Cycle settings is not set. So this operation is not permitted");
        }

        this.loanDecisionTransitionApiJsonValidator.validateCreateApprovalMatrix(command.json());

        final String currency = command.stringValueOfParameterNamed(LoanApprovalMatrixConstants.currencyParameterName);
        LoanApprovalMatrix loanApprovalMatrix = this.loanApprovalMatrixRepository.findLoanApprovalMatrixByCurrency(currency);

        if (loanApprovalMatrix != null) {
            throw new GeneralPlatformDomainRuleException("error.msg.loan.approval.matrix.with.this.currency.already.exist.",
                    String.format("Loan Approval Matrix with Currency [ %s ] exist. Only One currency per Matrix is accepted", currency));
        }

        LoanApprovalMatrix loanApprovalMatrixFrom = loanDecisionAssembler.assembleLoanApprovalMatrixFrom(command);
        this.loanApprovalMatrixRepository.saveAndFlush(loanApprovalMatrixFrom);

        return new CommandProcessingResultBuilder() //
                .withCommandId(command.commandId()) //
                .withEntityId(loanApprovalMatrixFrom.getId()) //
                .withResourceIdAsString(loanApprovalMatrixFrom.getId().toString()).build();

    }

    @Override
    public CommandProcessingResult deleteLoanApprovalMatrix(Long matrixId) {
        Boolean isExtendLoanLifeCycleConfig = loanDecisionStateUtilService.getExtendLoanLifeCycleConfig().isEnabled();

        if (!isExtendLoanLifeCycleConfig) {
            throw new GeneralPlatformDomainRuleException("error.msg.Add-More-Stages-To-A-Loan-Life-Cycle.is.not.set",
                    "Add-More-Stages-To-A-Loan-Life-Cycle settings is not set. So this operation is not permitted");
        }

        LoanApprovalMatrix loanApprovalMatrix = this.loanApprovalMatrixRepository.findById(matrixId).orElseThrow();

        this.loanApprovalMatrixRepository.delete(loanApprovalMatrix);

        return new CommandProcessingResultBuilder() //
                .withEntityId(matrixId) //
                .withResourceIdAsString(matrixId.toString()).build();
    }

    @Override
    public CommandProcessingResult updateLoanApprovalMatrix(JsonCommand command, Long matrixId) {
        try {
            this.context.authenticatedUser();

            Boolean isExtendLoanLifeCycleConfig = loanDecisionStateUtilService.getExtendLoanLifeCycleConfig().isEnabled();

            if (!isExtendLoanLifeCycleConfig) {
                throw new GeneralPlatformDomainRuleException("error.msg.Add-More-Stages-To-A-Loan-Life-Cycle.is.not.set",
                        "Add-More-Stages-To-A-Loan-Life-Cycle settings is not set. So this operation is not permitted");
            }
            this.loanDecisionTransitionApiJsonValidator.validateUpdateApprovalMatrix(command.json());

            LoanApprovalMatrix loanApprovalMatrix = this.loanApprovalMatrixRepository.findById(matrixId).orElseThrow();

            final String currency = command.stringValueOfParameterNamed(LoanApprovalMatrixConstants.currencyParameterName);

            if (!currency.equals(loanApprovalMatrix.getCurrency())) {
                LoanApprovalMatrix matrixCurrency = this.loanApprovalMatrixRepository.findLoanApprovalMatrixByCurrency(currency);

                if (matrixCurrency != null && !matrixCurrency.getId().equals(loanApprovalMatrix.getId())) {
                    throw new GeneralPlatformDomainRuleException("error.msg.loan.approval.matrix.with.this.currency.already.exist.", String
                            .format("Loan Approval Matrix with Currency [ %s ] exist. Only One currency per Matrix is accepted", currency));
                }
            }

            // Update legacy fields (levels 1-5) for backward compatibility
            final Map<String, Object> changes = loanApprovalMatrix.update(command);

            // Update dynamic levels (supports levels 1-5 and beyond)
            updateDynamicApprovalMatrixLevels(command, loanApprovalMatrix, changes);

            if (!changes.isEmpty()) {
                this.loanApprovalMatrixRepository.saveAndFlush(loanApprovalMatrix);
            }

            return new CommandProcessingResultBuilder() //
                    .withCommandId(command.commandId()) //
                    .withResourceIdAsString(loanApprovalMatrix.getId().toString()) //
                    .withEntityId(loanApprovalMatrix.getId()) //
                    .with(changes) //
                    .build();
        } catch (JpaSystemException | PersistenceException ex) {
            return CommandProcessingResult.empty();
        }
    }

    /**
     * Updates dynamic approval matrix levels from the command JSON.
     * This method handles updates to the m_loan_approval_matrix_level table for all IC review levels.
     *
     * This method now supports levels 6 through MAX_DYNAMIC_LEVEL even without pre-configuration.
     * If level parameters are provided for a level that doesn't exist in the database,
     * the level configuration will be auto-created.
     */
    private void updateDynamicApprovalMatrixLevels(JsonCommand command, LoanApprovalMatrix approvalMatrix, Map<String, Object> changes) {
        // Get all active IC review levels from database
        List<IcReviewLevelConfig> activeLevels = icReviewLevelConfigRepository.findAllActiveOrderByDisplayOrder();

        // Track which levels we've processed from the database
        java.util.Set<Integer> processedLevels = new java.util.HashSet<>();

        // Process existing active levels from database
        for (IcReviewLevelConfig levelConfig : activeLevels) {
            Integer levelNumber = levelConfig.getLevelNumber();
            processedLevels.add(levelNumber);
            processLevelUpdate(command, approvalMatrix, levelConfig, levelNumber, changes);
        }

        // Also check for dynamic levels (6 through MAX_DYNAMIC_LEVEL) that might not be in the database
        // This allows accepting level parameters without requiring pre-configuration
        for (int levelNumber = 6; levelNumber <= LoanApprovalMatrixConstants.MAX_DYNAMIC_LEVEL; levelNumber++) {
            if (!processedLevels.contains(levelNumber)) {
                String levelPrefix = "level" + getLevelName(levelNumber);
                // Check if any parameter for this level exists in the command
                if (hasLevelParametersInCommand(command, levelPrefix)) {
                    // Auto-create the IC review level config for this level
                    IcReviewLevelConfig newLevelConfig = createDynamicLevelConfig(levelNumber);
                    processLevelUpdate(command, approvalMatrix, newLevelConfig, levelNumber, changes);
                }
            }
        }
    }

    /**
     * Check if command contains any parameters for the given level prefix
     */
    private boolean hasLevelParametersInCommand(JsonCommand command, String levelPrefix) {
        return command.parameterExists(levelPrefix + "UnsecuredFirstCycleMaxAmount")
                || command.parameterExists(levelPrefix + "UnsecuredSecondCycleMaxAmount")
                || command.parameterExists(levelPrefix + "SecuredFirstCycleMaxAmount")
                || command.parameterExists(levelPrefix + "SecuredSecondCycleMaxAmount")
                || command.parameterExists(levelPrefix + "UnsecuredFirstCycleMinTerm")
                || command.parameterExists(levelPrefix + "UnsecuredFirstCycleMaxTerm")
                || command.parameterExists(levelPrefix + "UnsecuredSecondCycleMinTerm")
                || command.parameterExists(levelPrefix + "UnsecuredSecondCycleMaxTerm")
                || command.parameterExists(levelPrefix + "SecuredFirstCycleMinTerm")
                || command.parameterExists(levelPrefix + "SecuredFirstCycleMaxTerm")
                || command.parameterExists(levelPrefix + "SecuredSecondCycleMinTerm")
                || command.parameterExists(levelPrefix + "SecuredSecondCycleMaxTerm");
    }

    /**
     * Auto-create an IC Review Level Configuration for a dynamic level.
     * This allows accepting level parameters without requiring pre-configuration in the database.
     */
    private IcReviewLevelConfig createDynamicLevelConfig(int levelNumber) {
        // Check if level already exists (another thread might have created it)
        IcReviewLevelConfig existingConfig = icReviewLevelConfigRepository.findByLevelNumber(levelNumber);
        if (existingConfig != null) {
            return existingConfig;
        }

        // Calculate decision state value
        // Levels 1-5: 1400, 1500, 1600, 1700, 1800 (standard 100 increments)
        // Levels 6+: 1801, 1802, 1803, etc. (fit between 1800 and 1899, before PREPARE_AND_SIGN_CONTRACT at 1900)
        int decisionStateValue;
        if (levelNumber <= 5) {
            decisionStateValue = 1300 + (levelNumber * 100); // 1400, 1500, 1600, 1700, 1800
        } else {
            decisionStateValue = 1800 + (levelNumber - 5); // 1801, 1802, 1803, etc.
        }

        // Create new level config
        IcReviewLevelConfig newConfig = new IcReviewLevelConfig(
                levelNumber,
                "IC Review Level " + levelNumber,
                "IC_REVIEW_LEVEL_" + levelNumber,
                decisionStateValue,
                true,
                levelNumber // displayOrder same as level number
        );

        return icReviewLevelConfigRepository.saveAndFlush(newConfig);
    }

    /**
     * Process level update for a specific level configuration
     */
    private void processLevelUpdate(JsonCommand command, LoanApprovalMatrix approvalMatrix,
                                     IcReviewLevelConfig levelConfig, Integer levelNumber, Map<String, Object> changes) {
        String levelPrefix = "level" + getLevelName(levelNumber);

        // Debug logging to trace parameter matching
        log.info("Processing level {} update. levelPrefix='{}', Looking for parameter: '{}'",
                levelNumber, levelPrefix, levelPrefix + "UnsecuredFirstCycleMaxAmount");

        // Check if any field for this level is being updated
        boolean hasLevelUpdate = false;
        BigDecimal unsecuredFirstCycleMaxAmount = null;
        Integer unsecuredFirstCycleMinTerm = null;
        Integer unsecuredFirstCycleMaxTerm = null;
        BigDecimal unsecuredSecondCycleMaxAmount = null;
        Integer unsecuredSecondCycleMinTerm = null;
        Integer unsecuredSecondCycleMaxTerm = null;
        BigDecimal securedFirstCycleMaxAmount = null;
        Integer securedFirstCycleMinTerm = null;
        Integer securedFirstCycleMaxTerm = null;
        BigDecimal securedSecondCycleMaxAmount = null;
        Integer securedSecondCycleMinTerm = null;
        Integer securedSecondCycleMaxTerm = null;

        // Extract values from command if present
        String unsecuredFirstMaxParam = levelPrefix + "UnsecuredFirstCycleMaxAmount";
        String unsecuredFirstMinTermParam = levelPrefix + "UnsecuredFirstCycleMinTerm";
        String unsecuredFirstMaxTermParam = levelPrefix + "UnsecuredFirstCycleMaxTerm";
        String unsecuredSecondMaxParam = levelPrefix + "UnsecuredSecondCycleMaxAmount";
        String unsecuredSecondMinTermParam = levelPrefix + "UnsecuredSecondCycleMinTerm";
        String unsecuredSecondMaxTermParam = levelPrefix + "UnsecuredSecondCycleMaxTerm";
        String securedFirstMaxParam = levelPrefix + "SecuredFirstCycleMaxAmount";
        String securedFirstMinTermParam = levelPrefix + "SecuredFirstCycleMinTerm";
        String securedFirstMaxTermParam = levelPrefix + "SecuredFirstCycleMaxTerm";
        String securedSecondMaxParam = levelPrefix + "SecuredSecondCycleMaxAmount";
        String securedSecondMinTermParam = levelPrefix + "SecuredSecondCycleMinTerm";
        String securedSecondMaxTermParam = levelPrefix + "SecuredSecondCycleMaxTerm";

        if (command.parameterExists(unsecuredFirstMaxParam)) {
            unsecuredFirstCycleMaxAmount = command.bigDecimalValueOfParameterNamed(unsecuredFirstMaxParam);
            log.info("Found parameter '{}' with value: {}", unsecuredFirstMaxParam, unsecuredFirstCycleMaxAmount);
            hasLevelUpdate = true;
        }
        if (command.parameterExists(unsecuredFirstMinTermParam)) {
            unsecuredFirstCycleMinTerm = command.integerValueOfParameterNamed(unsecuredFirstMinTermParam);
            hasLevelUpdate = true;
        }
        if (command.parameterExists(unsecuredFirstMaxTermParam)) {
            unsecuredFirstCycleMaxTerm = command.integerValueOfParameterNamed(unsecuredFirstMaxTermParam);
            hasLevelUpdate = true;
        }
        if (command.parameterExists(unsecuredSecondMaxParam)) {
            unsecuredSecondCycleMaxAmount = command.bigDecimalValueOfParameterNamed(unsecuredSecondMaxParam);
            hasLevelUpdate = true;
        }
        if (command.parameterExists(unsecuredSecondMinTermParam)) {
            unsecuredSecondCycleMinTerm = command.integerValueOfParameterNamed(unsecuredSecondMinTermParam);
            hasLevelUpdate = true;
        }
        if (command.parameterExists(unsecuredSecondMaxTermParam)) {
            unsecuredSecondCycleMaxTerm = command.integerValueOfParameterNamed(unsecuredSecondMaxTermParam);
            hasLevelUpdate = true;
        }
        if (command.parameterExists(securedFirstMaxParam)) {
            securedFirstCycleMaxAmount = command.bigDecimalValueOfParameterNamed(securedFirstMaxParam);
            hasLevelUpdate = true;
        }
        if (command.parameterExists(securedFirstMinTermParam)) {
            securedFirstCycleMinTerm = command.integerValueOfParameterNamed(securedFirstMinTermParam);
            hasLevelUpdate = true;
        }
        if (command.parameterExists(securedFirstMaxTermParam)) {
            securedFirstCycleMaxTerm = command.integerValueOfParameterNamed(securedFirstMaxTermParam);
            hasLevelUpdate = true;
        }
        if (command.parameterExists(securedSecondMaxParam)) {
            securedSecondCycleMaxAmount = command.bigDecimalValueOfParameterNamed(securedSecondMaxParam);
            hasLevelUpdate = true;
        }
        if (command.parameterExists(securedSecondMinTermParam)) {
            securedSecondCycleMinTerm = command.integerValueOfParameterNamed(securedSecondMinTermParam);
            hasLevelUpdate = true;
        }
        if (command.parameterExists(securedSecondMaxTermParam)) {
            securedSecondCycleMaxTerm = command.integerValueOfParameterNamed(securedSecondMaxTermParam);
            hasLevelUpdate = true;
        }

        if (hasLevelUpdate) {
            // Find or create the matrix level entry
            LoanApprovalMatrixLevel matrixLevel = loanApprovalMatrixLevelRepository
                    .findByApprovalMatrixIdAndLevelNumber(approvalMatrix.getId(), levelNumber);

            if (matrixLevel == null) {
                // Create new entry
                matrixLevel = new LoanApprovalMatrixLevel();
                matrixLevel.setApprovalMatrix(approvalMatrix);
                matrixLevel.setIcReviewLevel(levelConfig);
                matrixLevel.setLevelNumber(levelNumber);
            }

            // Update fields that were provided
            if (unsecuredFirstCycleMaxAmount != null) {
                matrixLevel.setUnsecuredFirstCycleMaxAmount(unsecuredFirstCycleMaxAmount);
            }
            if (unsecuredFirstCycleMinTerm != null) {
                matrixLevel.setUnsecuredFirstCycleMinTerm(unsecuredFirstCycleMinTerm);
            }
            if (unsecuredFirstCycleMaxTerm != null) {
                matrixLevel.setUnsecuredFirstCycleMaxTerm(unsecuredFirstCycleMaxTerm);
            }
            if (unsecuredSecondCycleMaxAmount != null) {
                matrixLevel.setUnsecuredSecondCycleMaxAmount(unsecuredSecondCycleMaxAmount);
            }
            if (unsecuredSecondCycleMinTerm != null) {
                matrixLevel.setUnsecuredSecondCycleMinTerm(unsecuredSecondCycleMinTerm);
            }
            if (unsecuredSecondCycleMaxTerm != null) {
                matrixLevel.setUnsecuredSecondCycleMaxTerm(unsecuredSecondCycleMaxTerm);
            }
            if (securedFirstCycleMaxAmount != null) {
                matrixLevel.setSecuredFirstCycleMaxAmount(securedFirstCycleMaxAmount);
            }
            if (securedFirstCycleMinTerm != null) {
                matrixLevel.setSecuredFirstCycleMinTerm(securedFirstCycleMinTerm);
            }
            if (securedFirstCycleMaxTerm != null) {
                matrixLevel.setSecuredFirstCycleMaxTerm(securedFirstCycleMaxTerm);
            }
            if (securedSecondCycleMaxAmount != null) {
                matrixLevel.setSecuredSecondCycleMaxAmount(securedSecondCycleMaxAmount);
            }
            if (securedSecondCycleMinTerm != null) {
                matrixLevel.setSecuredSecondCycleMinTerm(securedSecondCycleMinTerm);
            }
            if (securedSecondCycleMaxTerm != null) {
                matrixLevel.setSecuredSecondCycleMaxTerm(securedSecondCycleMaxTerm);
            }

            loanApprovalMatrixLevelRepository.saveAndFlush(matrixLevel);
            log.info("Saved matrix level {} for approval matrix {}. ID: {}", levelNumber, approvalMatrix.getId(), matrixLevel.getId());
            changes.put("dynamicLevel" + levelNumber, "updated");
        } else {
            log.info("No updates found for level {} in command parameters", levelNumber);
        }
    }

    /**
     * Converts level number to level name (e.g., 1 -> "One", 2 -> "Two", etc.)
     * Supports levels 1-20 with spelled-out names to match the API parameter convention.
     */
    private String getLevelName(Integer levelNumber) {
        switch (levelNumber) {
            case 1: return "One";
            case 2: return "Two";
            case 3: return "Three";
            case 4: return "Four";
            case 5: return "Five";
            case 6: return "Six";
            case 7: return "Seven";
            case 8: return "Eight";
            case 9: return "Nine";
            case 10: return "Ten";
            case 11: return "Eleven";
            case 12: return "Twelve";
            case 13: return "Thirteen";
            case 14: return "Fourteen";
            case 15: return "Fifteen";
            case 16: return "Sixteen";
            case 17: return "Seventeen";
            case 18: return "Eighteen";
            case 19: return "Nineteen";
            case 20: return "Twenty";
            default: return levelNumber.toString();
        }
    }

    @Override
    public CommandProcessingResult acceptLoanApplicationReview(final Long loanId, final JsonCommand command) {

        final AppUser currentUser = getAppUserIfPresent();

        this.loanDecisionTransitionApiJsonValidator.validateApplicationReview(command.json());

        final Loan loan = this.loanRepositoryWrapper.findOneWithNotFoundDetection(loanId, true);
        final LoanDecision loanDecision = this.loanDecisionRepository.findLoanDecisionByLoanId(loan.getId());

        validateReviewApplicationBusinessRule(command, loan, loanDecision);
        LoanDecision loanDecisionObj = loanDecisionAssembler.assembleFrom(command, loan, currentUser);
        LoanDecision savedObj = loanDecisionRepository.saveAndFlush(loanDecisionObj);

        Loan loanObj = loan;
        loanObj.setLoanDecisionState(LoanDecisionState.REVIEW_APPLICATION.getValue());
        this.loanRepositoryWrapper.saveAndFlush(loanObj);

        Note note = null;
        if (StringUtils.isNotBlank(loanDecisionObj.getReviewApplicationNote())) {
            note = Note.loanNote(loanObj, "Review Application: " + loanDecisionObj.getReviewApplicationNote());
            this.noteRepository.save(note);
        }

        this.businessEventNotifierService.notifyPostBusinessEvent(
                new LoanDecisionAcceptedEvent(loan, savedObj, note));

        return new CommandProcessingResultBuilder() //
                .withCommandId(command.commandId()) //
                .withEntityId(savedObj.getId()) //
                .withOfficeId(loan.getOfficeId()) //
                .withClientId(loan.getClientId()) //
                .withGroupId(loan.getGroupId()) //
                .withLoanId(loanId) //
                .withResourceIdAsString(savedObj.getId().toString()).build();
    }

    @Override
    public CommandProcessingResult rejectLoanApplicationReview(final Long loanId, final JsonCommand command){

        // Validate the current state
        final Loan loan = this.loanRepositoryWrapper.findOneWithNotFoundDetection(loanId, true);
        final LoanDecision loanDecision = this.loanDecisionRepository.findLoanDecisionByLoanId(loan.getId());

        if (!LoanDecisionState.fromInt(loan.getLoanDecisionState()).isReviewApplication()) {
            throw new GeneralPlatformDomainRuleException("error.msg.loan.invalid.state",
                    "Loan is not in the Review Application stage.");
        }

        // Delete decision and revert to initial state
        loan.setLoanDecisionState(null);
        loanDecisionRepository.delete(loanDecision);

        Note note = null;
        // Add and save the note
        if (StringUtils.isNotBlank(command.stringValueOfParameterNamed("note"))) {
            note = Note.loanNote(loan, "Review Application Returned: " + command.stringValueOfParameterNamed("note"));
            this.noteRepository.save(note);
        }

        // Save changes
        this.loanRepositoryWrapper.saveAndFlush(loan);

        // Notify business event
        this.businessEventNotifierService.notifyPostBusinessEvent(new LoanDecisionRejectEvent(loan, loanDecision, note));

        return new CommandProcessingResultBuilder()
                .withCommandId(command.commandId())
                .withEntityId(loanDecision.getId())
                .withOfficeId(loan.getOfficeId())
                .withClientId(loan.getClientId())
                .withGroupId(loan.getGroupId())
                .withLoanId(loanId)
                .withResourceIdAsString(loanDecision.getId().toString())
                .build();
    }

    @Override
    public CommandProcessingResult applyDueDiligence(Long loanId, JsonCommand command) {

        final AppUser currentUser = getAppUserIfPresent();

        this.loanDecisionTransitionApiJsonValidator.validateDueDiligence(command.json());
        final Loan loan = this.loanRepositoryWrapper.findOneWithNotFoundDetection(loanId, true);
        Boolean isIdeaClient = command.booleanObjectValueOfParameterNamed(LoanApiConstants.isIdeaClientParamName);
        if (isIdeaClient == null) {
            isIdeaClient = Boolean.FALSE;
        }
        Boolean isCrbVerificationRequired = command.booleanObjectValueOfParameterNamed(LoanApiConstants.isCrbVerificationRequiredParamName);
        if (isCrbVerificationRequired == null) {
            isCrbVerificationRequired = Boolean.FALSE;
        }

        // CRB Verification is required for Strata other than Refugee
        final GlobalConfigurationPropertyData otherInfoConfig = this.configurationReadPlatformService
                .retrieveGlobalConfiguration("Enable-other-client-info");
        final Boolean isClientOtherInfoEnable = otherInfoConfig.isEnabled();
        if (isClientOtherInfoEnable) {
            final Long clientId = loan.getClientId();
            final ClientOtherInfoData clientOtherInfoData = this.clientOtherInfoReadPlatformService.retrieveByClientId(clientId);
            if (clientOtherInfoData == null) {
                throw new ClientOtherInfoNotFoundException(loan.getId(), clientId);
            }
            final CodeValueData strata = clientOtherInfoData.getStrata();
            if (!strata.getName().equalsIgnoreCase("Refugee") && !isCrbVerificationRequired) {
                throw new LoanDueDiligenceException("error.msg.required.crb.verification.if.strata.is.not.refugee",
                        "CRB Verification required because client is not Refugee. " + " Please change client's Strata to Refugee. "
                                + " If you want to ignore CRB Verification");

            }
        }

        // Do validation for kiva loans
        Fund fund = loan.getFund();
        if (fund != null && fund.getName().equalsIgnoreCase("kiva")) {
            kivaLoanService.validateLoanKivaDetails(loan);
        }

        final LoanDecision loanDecision = this.loanDecisionRepository.findLoanDecisionByLoanId(loan.getId());
        // Check CRB Verification if required
        if (isCrbVerificationRequired) {
            loanDecision.setCrbVerificationRequired(true);
            if (loan.getCurrencyCode().equalsIgnoreCase("KES")) {
                List<MetropolCrbIdentityReport> metropolCrbIdentityReportList = metropolCrbIdentityVerificationRepository
                        .findByLoanId(loan.getId());
                if (metropolCrbIdentityReportList.isEmpty()) {
                    throw new LoanDueDiligenceException("error.msg.required.crb.verification", "CRB Verification required.");
                }
            } else if (loan.getCurrencyCode().equalsIgnoreCase("RWF")) {
                // transunion
                List<TransunionCrbHeader> transunionCrbHeaderList = transunionCrbHeaderRepository.findByLoanId(loan.getId());
                if (transunionCrbHeaderList.isEmpty()) {
                    throw new LoanDueDiligenceException("error.msg.required.crb.verification", "CRB Verification required.");
                }
            }
        } else {
            loanDecision.setCrbVerificationRequired(false);
        }

        final BigDecimal recommendedAmount = command
                .bigDecimalValueOfParameterNamed(LoanApiConstants.dueDiligenceRecommendedAmountParameterName);
        final Integer termFrequency = command.integerValueOfParameterNamed(LoanApiConstants.recommendedLoanTermFrequencyParameterName);
        final Integer termPeriodFrequencyEnum = command
                .integerValueOfParameterNamed(LoanApiConstants.recommendedLoanTermFrequencyTypeParameterName);

        if (!isIdeaClient) {
            loanDecision.setIdeaClient(false);
            // check for cashflow and financial ratio. Idea Client does not have a cashflow/ balancesheet

            LoanCashFlowReport cashFlowReport = this.loanReadPlatformService.retrieveCashFlowReport(loanId);
            if (CollectionUtils.isEmpty(cashFlowReport.getCashFlowProjectionDataList())) {
                throw new LoanDueDiligenceException("error.msg.loan.required.cashflow.data", "CashFlow data not available.");
            }
            LoanFinancialRatioData financialRatioData = this.loanReadPlatformService.findLoanFinancialRatioDataByLoanId(loanId);
            if (financialRatioData == null) {
                throw new LoanDueDiligenceException("error.msg.loan.required.financialRatio.data", "Financial Ratio data not available.");
            }

            // Get calculated amount from CashFlow Projection Report
            BigDecimal calculatedAmount = this.loanDecisionStateUtilService.getMaxLoanAmountFromCashFlow(loan);
            if (recommendedAmount.compareTo(calculatedAmount) > 0) {
                throw new PlatformDataIntegrityException("error.msg.loan.recommended.amount.cannot.be.greater.than.calculated.amount",
                        "Recommended amount cannot be greater than the calculated amount", calculatedAmount);
            }
            validateRecommendedAmountShouldNotBeGreaterThanProposedAmount(loan.getProposedPrincipal(), recommendedAmount);
        } else {
            loanDecision.setIdeaClient(true);
            validateRecommendedAmountShouldNotBeGreaterThanProposedAmount(loan.getProposedPrincipal(), recommendedAmount);
        }


        validateDueDiligenceBusinessRule(command, loan, loanDecision);

        LoanDecision loanDecisionObj = loanDecisionAssembler.assembleDueDiligenceFrom(command, currentUser, loanDecision);

        loanDecisionObj.setNextLoanIcReviewDecisionState(LoanDecisionState.IC_REVIEW_LEVEL_ONE.getValue());
        Integer nextStage = loanDecisionObj.getNextLoanIcReviewDecisionState();
        final AppUser nextApprover = getNextApprover(command, LoanDecisionState.fromInt(nextStage));
        setNextApprover(loanDecisionObj,nextStage,nextApprover);

        LoanDecision savedObj = loanDecisionRepository.saveAndFlush(loanDecisionObj);

        Loan loanObj = loan;
        loanObj.setLoanDecisionState(LoanDecisionState.DUE_DILIGENCE.getValue());
        this.loanRepositoryWrapper.saveAndFlush(loanObj);

        Note note = null;
        if (StringUtils.isNotBlank(loanDecisionObj.getDueDiligenceNote())) {
            note = Note.loanNote(loanObj,
                    "Due Diligence : " + loanDecisionObj.getDueDiligenceNote() + " Recommended Amount : " + recommendedAmount + " "
                            + loan.getCurrencyCode() + " Loan Term : " + termFrequency + " "
                            + PeriodFrequencyType.fromInt(termPeriodFrequencyEnum));
            this.noteRepository.save(note);
        }

        this.businessEventNotifierService.notifyPostBusinessEvent(
                new LoanDecisionAcceptedEvent(loan, savedObj, note));

        return new CommandProcessingResultBuilder() //
                .withCommandId(command.commandId()) //
                .withEntityId(savedObj.getId()) //
                .withOfficeId(loan.getOfficeId()) //
                .withClientId(loan.getClientId()) //
                .withGroupId(loan.getGroupId()) //
                .withLoanId(loanId) //
                .withResourceIdAsString(savedObj.getId().toString()).build();
    }

    @Override
    public CommandProcessingResult rejectDueDiligence(Long loanId, JsonCommand command){
        final AppUser currentUser = getAppUserIfPresent();

        // Validate the current state
        final Loan loan = this.loanRepositoryWrapper.findOneWithNotFoundDetection(loanId, true);
        final LoanDecision loanDecision = this.loanDecisionRepository.findLoanDecisionByLoanId(loan.getId());

        if (!LoanDecisionState.fromInt(loan.getLoanDecisionState()).isDueDiligence()) {
            throw new GeneralPlatformDomainRuleException("error.msg.loan.invalid.state",
                    "Loan is not in the Due Diligence stage.");
        }

        // Revert to the previous stage
        loan.setLoanDecisionState(LoanDecisionState.REVIEW_APPLICATION.getValue());
        loanDecision.setLoanDecisionState(LoanDecisionState.REVIEW_APPLICATION.getValue());
        loanDecision.setNextLoanIcReviewDecisionState(LoanDecisionState.DUE_DILIGENCE.getValue());
        loanDecision.setRejectDueDiligence(true);

        Note note = null;
        // Add and save the note
        if (StringUtils.isNotBlank(command.stringValueOfParameterNamed("note"))) {
            note = Note.loanNote(loan, "Due Diligence Returned: " + command.stringValueOfParameterNamed("note"));
            this.noteRepository.save(note);
        }

        // Save changes
        this.loanRepositoryWrapper.saveAndFlush(loan);
        this.loanDecisionRepository.saveAndFlush(loanDecision);

        // Notify business event
        this.businessEventNotifierService.notifyPostBusinessEvent(new LoanDecisionRejectEvent(loan, loanDecision, note));

        return new CommandProcessingResultBuilder()
                .withCommandId(command.commandId())
                .withEntityId(loanDecision.getId())
                .withOfficeId(loan.getOfficeId())
                .withClientId(loan.getClientId())
                .withGroupId(loan.getGroupId())
                .withLoanId(loanId)
                .withResourceIdAsString(loanDecision.getId().toString())
                .build();
    }

    @Override
    public CommandProcessingResult acceptLoanCollateralReview(Long loanId, JsonCommand command) {

        final AppUser currentUser = getAppUserIfPresent();

        this.loanDecisionTransitionApiJsonValidator.validateCollateralReview(command.json());

        final Loan loan = this.loanRepositoryWrapper.findOneWithNotFoundDetection(loanId, true);
        final LoanDecision loanDecision = this.loanDecisionRepository.findLoanDecisionByLoanId(loan.getId());

        loanDecisionStateUtilService.validateCollateralReviewBusinessRule(command, loan, loanDecision);

        LoanDecision loanDecisionObj = loanDecisionAssembler.assembleCollateralReviewFrom(command, currentUser, loanDecision);
        LoanDecision savedObj = loanDecisionRepository.saveAndFlush(loanDecisionObj);

        Loan loanObj = loan;
        loanObj.setLoanDecisionState(LoanDecisionState.COLLATERAL_REVIEW.getValue());
        this.loanRepositoryWrapper.saveAndFlush(loanObj);

        Note note = null;
        if (StringUtils.isNotBlank(loanDecisionObj.getCollateralReviewNote())) {
            note = Note.loanNote(loanObj, "Collateral Review : " + loanDecisionObj.getCollateralReviewNote());
            this.noteRepository.save(note);
        }

        this.businessEventNotifierService.notifyPostBusinessEvent(
                new LoanDecisionAcceptedEvent(loan, savedObj, note));

        return new CommandProcessingResultBuilder() //
                .withCommandId(command.commandId()) //
                .withEntityId(savedObj.getId()) //
                .withOfficeId(loan.getOfficeId()) //
                .withClientId(loan.getClientId()) //
                .withGroupId(loan.getGroupId()) //
                .withLoanId(loanId) //
                .withResourceIdAsString(savedObj.getId().toString()).build();
    }

    @Override
    public CommandProcessingResult rejectLoanCollateralReview(Long loanId, JsonCommand command){
        final AppUser currentUser = getAppUserIfPresent();

        // Validate the current state
        final Loan loan = this.loanRepositoryWrapper.findOneWithNotFoundDetection(loanId, true);
        final LoanDecision loanDecision = this.loanDecisionRepository.findLoanDecisionByLoanId(loan.getId());

        if (!LoanDecisionState.fromInt(loan.getLoanDecisionState()).isDueDiligence()) {
            throw new GeneralPlatformDomainRuleException("error.msg.loan.invalid.state",
                    "Loan is not in the Collateral Review stage.");
        }

        // Revert to the previous stage
        loan.setLoanDecisionState(LoanDecisionState.DUE_DILIGENCE.getValue());
        loanDecision.setNextLoanIcReviewDecisionState(LoanDecisionState.COLLATERAL_REVIEW.getValue());
        loanDecision.setRejectCollateralReviewSigned(true);

        Note note = null;
        // Add and save the note
        if (StringUtils.isNotBlank(command.stringValueOfParameterNamed("note"))) {
            note = Note.loanNote(loan, "Collateral Review Returned: " + command.stringValueOfParameterNamed("note"));
            this.noteRepository.save(note);
        }

        // Save changes
        this.loanRepositoryWrapper.saveAndFlush(loan);
        this.loanDecisionRepository.saveAndFlush(loanDecision);

        // Notify business event
        this.businessEventNotifierService.notifyPostBusinessEvent(new LoanDecisionRejectEvent(loan, loanDecision, note));

        return new CommandProcessingResultBuilder()
                .withCommandId(command.commandId())
                .withEntityId(loanDecision.getId())
                .withOfficeId(loan.getOfficeId())
                .withClientId(loan.getClientId())
                .withGroupId(loan.getGroupId())
                .withLoanId(loanId)
                .withResourceIdAsString(loanDecision.getId().toString())
                .build();
    }

    @Override
    public CommandProcessingResult acceptIcReviewDecisionLevelOne(Long loanId, JsonCommand command) {
        final AppUser currentUser = getAppUserIfPresent();

        this.loanDecisionTransitionApiJsonValidator.validateIcReviewStage(command.json());

        final Loan loan = this.loanRepositoryWrapper.findOneWithNotFoundDetection(loanId, true);
        final LoanDecision loanDecision = this.loanDecisionRepository.findLoanDecisionByLoanId(loan.getId());

        LocalDate icReviewOn = command.localDateValueOfParameterNamed(LoanApiConstants.icReviewOnDateParameterName);
        final BigDecimal recommendedAmount = command.bigDecimalValueOfParameterNamed(LoanApiConstants.icReviewRecommendedAmount);
        final Integer termFrequency = command.integerValueOfParameterNamed(LoanApiConstants.icReviewTermFrequency);
        final Integer termPeriodFrequencyEnum = command.integerValueOfParameterNamed(LoanApiConstants.icReviewTermPeriodFrequencyEnum);

        loanDecisionStateUtilService.validateIcReviewDecisionLevelOneBusinessRule(command, loan, loanDecision, icReviewOn);
        LoanApprovalMatrix approvalMatrix = this.loanApprovalMatrixRepository.findLoanApprovalMatrixByCurrency(loan.getCurrencyCode());

        if (approvalMatrix == null) {
            throw new GeneralPlatformDomainRuleException("error.msg.loan.approval.matrix.with.this.currency.does.not.exist.",
                    String.format("Loan Approval Matrix with Currency [ %s ] doesn't exist. Approval matrix is expected to continue ",
                            loan.getCurrencyCode()));
        }

        if (!loanDecision.getIdeaClient()) {
            final BigDecimal maxLoanAmountFromCashFlow = loanDecisionStateUtilService.getMaxLoanAmountFromCashFlow(loan);
            if (recommendedAmount.compareTo(maxLoanAmountFromCashFlow) > 0) {
                throw new GeneralPlatformDomainRuleException(
                        "error.msg.loan.ic.review.recommended.amount.can.not.greater.than.auto.computed.amount",
                        "Recommended amount can not be greater than auto-computed recommended amount", maxLoanAmountFromCashFlow);
            }
        }

        // Get Loan Matrix
        // Determine which cycle of this Loan Account
        // Determine the Next Level or stage to review
        // Add custom Params in Decision Table
        List<Loan> loanIndividualCounter = loanDecisionStateUtilService.getLoanCounter(loan);

        Boolean isLoanFirstCycle = loanDecisionStateUtilService.isLoanFirstCycle(loanIndividualCounter);
        Boolean isLoanUnsecure = loanDecisionStateUtilService.isLoanUnSecure(loan);
        final BigDecimal dueDiligenceRecommendedAmount = loanDecision.getDueDiligenceRecommendedAmount();

        loanDecisionStateUtilService.validateLoanAccountToComplyToApprovalMatrixStage(loan, approvalMatrix, isLoanFirstCycle,
                isLoanUnsecure, LoanDecisionState.IC_REVIEW_LEVEL_ONE, dueDiligenceRecommendedAmount);
        // generate the next stage based on loan approval matrix via amounts to be disbursed
        loanDecisionStateUtilService.determineTheNextDecisionStage(loan, loanDecision, approvalMatrix, isLoanFirstCycle, isLoanUnsecure,
                LoanDecisionState.IC_REVIEW_LEVEL_ONE, dueDiligenceRecommendedAmount);

        final Integer nextDecisionStage = loanDecision.getNextLoanIcReviewDecisionState();
        if (nextDecisionStage.equals(LoanDecisionState.PREPARE_AND_SIGN_CONTRACT.getValue())) {
            final Map<String, Object> changes = loan.loanApplicationICReview(currentUser, command);
            if (!changes.isEmpty()) {
                LocalDate recalculateFrom = null;
                ScheduleGeneratorDTO scheduleGeneratorDTO = this.loanUtilService.buildScheduleGeneratorDTO(loan, recalculateFrom);
                loan.regenerateRepaymentSchedule(scheduleGeneratorDTO);
            }
        }

        LoanDecision loanDecisionObj = loanDecisionAssembler.assembleIcReviewDecisionLevelOneFrom(command, currentUser, loanDecision, false,
                icReviewOn, recommendedAmount, termFrequency, termPeriodFrequencyEnum);

        Integer nextStage = loanDecisionObj.getNextLoanIcReviewDecisionState();
        final AppUser nextApprover = getNextApprover(command, LoanDecisionState.fromInt(nextStage));
        setNextApprover(loanDecisionObj,nextStage,nextApprover);

        LoanDecision savedObj = loanDecisionRepository.saveAndFlush(loanDecisionObj);

        Loan loanObj = loan;
        loanObj.setLoanDecisionState(LoanDecisionState.IC_REVIEW_LEVEL_ONE.getValue());
        this.loanRepositoryWrapper.saveAndFlush(loanObj);

        Note note = null;
        if (StringUtils.isNotBlank(loanDecisionObj.getIcReviewDecisionLevelOneNote())) {
            note = Note.loanNote(loanObj,
                    "Approve IC Review-Decision Level One : " + loanDecisionObj.getIcReviewDecisionLevelOneNote() + " Recommended Amount : "
                            + recommendedAmount + " " + loan.getCurrencyCode() + " Loan Term : " + termFrequency + " "
                            + PeriodFrequencyType.fromInt(termPeriodFrequencyEnum));
            this.noteRepository.save(note);
        }
        validateRecommendedAmountShouldNotBeGreaterThanProposedAmount(loan.getProposedPrincipal(), recommendedAmount);

        this.businessEventNotifierService.notifyPostBusinessEvent(
                new LoanDecisionAcceptedEvent(loan, savedObj, note));

        return new CommandProcessingResultBuilder() //
                .withCommandId(command.commandId()) //
                .withEntityId(savedObj.getId()) //
                .withOfficeId(loan.getOfficeId()) //
                .withClientId(loan.getClientId()) //
                .withGroupId(loan.getGroupId()) //
                .withLoanId(loanId) //
                .withResourceIdAsString(savedObj.getId().toString()).build();
    }

    @Override
    public CommandProcessingResult rejectIcReviewDecisionLevelOne(Long loanId, JsonCommand command) {
        final AppUser currentUser = getAppUserIfPresent();

        // Validate the current state
        final Loan loan = this.loanRepositoryWrapper.findOneWithNotFoundDetection(loanId, true);
        final LoanDecision loanDecision = this.loanDecisionRepository.findLoanDecisionByLoanId(loan.getId());

        if (!LoanDecisionState.fromInt(loan.getLoanDecisionState()).isIcReviewLevelOne()) {
            throw new GeneralPlatformDomainRuleException("error.msg.loan.decision.state.invalid.for.reject",
                    "Loan Decision state is invalid for reject operation. Expected IC_REVIEW_LEVEL_ONE.");
        }

        // Revert to the previous stage
        loan.setLoanDecisionState(LoanDecisionState.DUE_DILIGENCE.getValue());
        loanDecision.setLoanDecisionState(LoanDecisionState.DUE_DILIGENCE.getValue());
        loanDecision.setNextLoanIcReviewDecisionState(LoanDecisionState.IC_REVIEW_LEVEL_ONE.getValue());
        loanDecision.setRejectIcReviewDecisionLevelOneSigned(true);

        Note note = null;
        final String noteText = command.stringValueOfParameterNamed("note");
        if (StringUtils.isNotBlank(noteText)) {
            note = Note.loanNote(loan, "Returned IC Review-Decision Level One : " + noteText);
            this.noteRepository.save(note);
        }

        // Save changes
        this.loanRepositoryWrapper.saveAndFlush(loan);
        this.loanDecisionRepository.saveAndFlush(loanDecision);

        // Notify business event
        this.businessEventNotifierService.notifyPostBusinessEvent(new LoanDecisionRejectEvent(loan, loanDecision, note));

        return new CommandProcessingResultBuilder()
                .withCommandId(command.commandId())
                .withEntityId(loanDecision.getId())
                .withOfficeId(loan.getOfficeId())
                .withClientId(loan.getClientId())
                .withGroupId(loan.getGroupId())
                .withLoanId(loanId)
                .withResourceIdAsString(loanDecision.getId().toString())
                .build();
    }

    @Override
    public CommandProcessingResult acceptIcReviewDecisionLevelTwo(Long loanId, JsonCommand command) {
        final AppUser currentUser = getAppUserIfPresent();

        this.loanDecisionTransitionApiJsonValidator.validateIcReviewStage(command.json());

        final Loan loan = this.loanRepositoryWrapper.findOneWithNotFoundDetection(loanId, true);
        final LoanDecision loanDecision = this.loanDecisionRepository.findLoanDecisionByLoanId(loan.getId());

        LocalDate icReviewOn = command.localDateValueOfParameterNamed(LoanApiConstants.icReviewOnDateParameterName);
        final BigDecimal recommendedAmount = command.bigDecimalValueOfParameterNamed(LoanApiConstants.icReviewRecommendedAmount);
        final Integer termFrequency = command.integerValueOfParameterNamed(LoanApiConstants.icReviewTermFrequency);
        final Integer termPeriodFrequencyEnum = command.integerValueOfParameterNamed(LoanApiConstants.icReviewTermPeriodFrequencyEnum);

        loanDecisionStateUtilService.validateIcReviewDecisionLevelTwoBusinessRule(command, loan, loanDecision, icReviewOn);
        LoanApprovalMatrix approvalMatrix = this.loanApprovalMatrixRepository.findLoanApprovalMatrixByCurrency(loan.getCurrencyCode());

        if (approvalMatrix == null) {
            throw new GeneralPlatformDomainRuleException("error.msg.loan.approval.matrix.with.this.currency.does.not.exist.",
                    String.format("Loan Approval Matrix with Currency [ %s ] doesn't exist. Approval matrix is expected to continue ",
                            loan.getCurrencyCode()));
        }

        if (!loanDecision.getIdeaClient()) {
            final BigDecimal maxLoanAmountFromCashFlow = loanDecisionStateUtilService.getMaxLoanAmountFromCashFlow(loan);
            if (recommendedAmount.compareTo(maxLoanAmountFromCashFlow) > 0) {
                throw new GeneralPlatformDomainRuleException(
                        "error.msg.loan.ic.review.recommended.amount.can.not.greater.than.auto.computed.amount",
                        "Recommended amount can not be greater than auto-computed recommended amount", maxLoanAmountFromCashFlow);
            }
        }

        // Get Loan Matrix
        // Determine which cycle of this Loan Account
        // Determine the Next Level or stage to review
        // Add custom Params in Decision Table
        List<Loan> loanIndividualCounter = loanDecisionStateUtilService.getLoanCounter(loan);

        Boolean isLoanFirstCycle = loanDecisionStateUtilService.isLoanFirstCycle(loanIndividualCounter);
        Boolean isLoanUnsecure = loanDecisionStateUtilService.isLoanUnSecure(loan);
        final BigDecimal dueDiligenceRecommendedAmount = loanDecision.getDueDiligenceRecommendedAmount();

        loanDecisionStateUtilService.validateLoanAccountToComplyToApprovalMatrixStage(loan, approvalMatrix, isLoanFirstCycle,
                isLoanUnsecure, LoanDecisionState.IC_REVIEW_LEVEL_TWO, dueDiligenceRecommendedAmount);
        // generate the next stage based on loan approval matrix via amounts to be disbursed
        loanDecisionStateUtilService.determineTheNextDecisionStage(loan, loanDecision, approvalMatrix, isLoanFirstCycle, isLoanUnsecure,
                LoanDecisionState.IC_REVIEW_LEVEL_TWO, dueDiligenceRecommendedAmount);

        final Integer nextDecisionStage = loanDecision.getNextLoanIcReviewDecisionState();
        if (nextDecisionStage.equals(LoanDecisionState.PREPARE_AND_SIGN_CONTRACT.getValue())) {
            final Map<String, Object> changes = loan.loanApplicationICReview(currentUser, command);
            if (!changes.isEmpty()) {
                LocalDate recalculateFrom = null;
                ScheduleGeneratorDTO scheduleGeneratorDTO = this.loanUtilService.buildScheduleGeneratorDTO(loan, recalculateFrom);
                loan.regenerateRepaymentSchedule(scheduleGeneratorDTO);
            }
        }

        LoanDecision loanDecisionObj = loanDecisionAssembler.assembleIcReviewDecisionLevelTwoFrom(command, currentUser, loanDecision,
                Boolean.FALSE, icReviewOn, recommendedAmount, termFrequency, termPeriodFrequencyEnum);

        Integer nextStage = loanDecisionObj.getNextLoanIcReviewDecisionState();
        final AppUser nextApprover = getNextApprover(command, LoanDecisionState.fromInt(nextStage));
        setNextApprover(loanDecisionObj,nextStage,nextApprover);

        LoanDecision savedObj = loanDecisionRepository.saveAndFlush(loanDecisionObj);

        Loan loanObj = loan;
        loanObj.setLoanDecisionState(LoanDecisionState.IC_REVIEW_LEVEL_TWO.getValue());
        this.loanRepositoryWrapper.saveAndFlush(loanObj);

        Note note = null;
        if (StringUtils.isNotBlank(loanDecisionObj.getIcReviewDecisionLevelTwoNote())) {
            note = Note.loanNote(loanObj,
                    "Approve IC Review-Decision Level Two : " + loanDecisionObj.getIcReviewDecisionLevelTwoNote() + " Recommended Amount : "
                            + recommendedAmount + " " + loan.getCurrencyCode() + " Loan Term : " + termFrequency + " "
                            + PeriodFrequencyType.fromInt(termPeriodFrequencyEnum));
            this.noteRepository.save(note);
        }
        validateRecommendedAmountShouldNotBeGreaterThanProposedAmount(loan.getProposedPrincipal(), recommendedAmount);

        this.businessEventNotifierService.notifyPostBusinessEvent(
                new LoanDecisionAcceptedEvent(loan, savedObj, note));

        return new CommandProcessingResultBuilder() //
                .withCommandId(command.commandId()) //
                .withEntityId(savedObj.getId()) //
                .withOfficeId(loan.getOfficeId()) //
                .withClientId(loan.getClientId()) //
                .withGroupId(loan.getGroupId()) //
                .withLoanId(loanId) //
                .withResourceIdAsString(savedObj.getId().toString()).build();
    }

    @Override
    public CommandProcessingResult rejectIcReviewDecisionLevelTwo(Long loanId, JsonCommand command) {
        final AppUser currentUser = getAppUserIfPresent();

        // Validate the current state
        final Loan loan = this.loanRepositoryWrapper.findOneWithNotFoundDetection(loanId, true);
        final LoanDecision loanDecision = this.loanDecisionRepository.findLoanDecisionByLoanId(loan.getId());

        if (!LoanDecisionState.fromInt(loan.getLoanDecisionState()).isIcReviewLevelTwo()) {
            throw new GeneralPlatformDomainRuleException("error.msg.loan.decision.state.invalid.for.reject",
                    "Loan Decision state is invalid for reject operation. Expected IC_REVIEW_LEVEL_TWO.");
        }

        // Revert to the previous stage
        loan.setLoanDecisionState(LoanDecisionState.IC_REVIEW_LEVEL_ONE.getValue());
        loanDecision.setLoanDecisionState(LoanDecisionState.IC_REVIEW_LEVEL_ONE.getValue());
        loanDecision.setNextLoanIcReviewDecisionState(LoanDecisionState.IC_REVIEW_LEVEL_TWO.getValue());
        loanDecision.setRejectIcReviewDecisionLevelTwoSigned(true);

        Note note = null;
        final String noteText = command.stringValueOfParameterNamed("note");
        if (StringUtils.isNotBlank(noteText)) {
           note = Note.loanNote(loan, "Returned IC Review-Decision Level Two : " + noteText);
            this.noteRepository.save(note);
        }

        // Save changes
        this.loanRepositoryWrapper.saveAndFlush(loan);
        this.loanDecisionRepository.saveAndFlush(loanDecision);

        // Notify business event
        this.businessEventNotifierService.notifyPostBusinessEvent(new LoanDecisionRejectEvent(loan, loanDecision, note));

        return new CommandProcessingResultBuilder()
                .withCommandId(command.commandId())
                .withEntityId(loanDecision.getId())
                .withOfficeId(loan.getOfficeId())
                .withClientId(loan.getClientId())
                .withGroupId(loan.getGroupId())
                .withLoanId(loanId)
                .withResourceIdAsString(loanDecision.getId().toString())
                .build();
    }

    @Override
    public CommandProcessingResult acceptIcReviewDecisionLevelThree(Long loanId, JsonCommand command) {
        final AppUser currentUser = getAppUserIfPresent();

        this.loanDecisionTransitionApiJsonValidator.validateIcReviewStage(command.json());

        final Loan loan = this.loanRepositoryWrapper.findOneWithNotFoundDetection(loanId, true);
        final LoanDecision loanDecision = this.loanDecisionRepository.findLoanDecisionByLoanId(loan.getId());

        LocalDate icReviewOn = command.localDateValueOfParameterNamed(LoanApiConstants.icReviewOnDateParameterName);
        final BigDecimal recommendedAmount = command.bigDecimalValueOfParameterNamed(LoanApiConstants.icReviewRecommendedAmount);
        final Integer termFrequency = command.integerValueOfParameterNamed(LoanApiConstants.icReviewTermFrequency);
        final Integer termPeriodFrequencyEnum = command.integerValueOfParameterNamed(LoanApiConstants.icReviewTermPeriodFrequencyEnum);

        loanDecisionStateUtilService.validateIcReviewDecisionLevelThreeBusinessRule(command, loan, loanDecision, icReviewOn);
        LoanApprovalMatrix approvalMatrix = this.loanApprovalMatrixRepository.findLoanApprovalMatrixByCurrency(loan.getCurrencyCode());

        if (approvalMatrix == null) {
            throw new GeneralPlatformDomainRuleException("error.msg.loan.approval.matrix.with.this.currency.does.not.exist.",
                    String.format("Loan Approval Matrix with Currency [ %s ] doesn't exist. Approval matrix is expected to continue ",
                            loan.getCurrencyCode()));
        }

        if (!loanDecision.getIdeaClient()) {
            final BigDecimal maxLoanAmountFromCashFlow = loanDecisionStateUtilService.getMaxLoanAmountFromCashFlow(loan);
            if (recommendedAmount.compareTo(maxLoanAmountFromCashFlow) > 0) {
                throw new GeneralPlatformDomainRuleException(
                        "error.msg.loan.ic.review.recommended.amount.can.not.greater.than.auto.computed.amount",
                        "Recommended amount can not be greater than auto-computed recommended amount", maxLoanAmountFromCashFlow);
            }
        }

        // Get Loan Matrix
        // Determine which cycle of this Loan Account
        // Determine the Next Level or stage to review
        // Add custom Params in Decision Table
        List<Loan> loanIndividualCounter = loanDecisionStateUtilService.getLoanCounter(loan);

        Boolean isLoanFirstCycle = loanDecisionStateUtilService.isLoanFirstCycle(loanIndividualCounter);
        Boolean isLoanUnsecure = loanDecisionStateUtilService.isLoanUnSecure(loan);
        final BigDecimal dueDiligenceRecommendedAmount = loanDecision.getDueDiligenceRecommendedAmount();

        loanDecisionStateUtilService.validateLoanAccountToComplyToApprovalMatrixStage(loan, approvalMatrix, isLoanFirstCycle,
                isLoanUnsecure, LoanDecisionState.IC_REVIEW_LEVEL_THREE, dueDiligenceRecommendedAmount);
        // generate the next stage based on loan approval matrix via amounts to be disbursed
        loanDecisionStateUtilService.determineTheNextDecisionStage(loan, loanDecision, approvalMatrix, isLoanFirstCycle, isLoanUnsecure,
                LoanDecisionState.IC_REVIEW_LEVEL_THREE, dueDiligenceRecommendedAmount);

        final Integer nextDecisionStage = loanDecision.getNextLoanIcReviewDecisionState();
        if (nextDecisionStage.equals(LoanDecisionState.PREPARE_AND_SIGN_CONTRACT.getValue())) {
            final Map<String, Object> changes = loan.loanApplicationICReview(currentUser, command);
            if (!changes.isEmpty()) {
                LocalDate recalculateFrom = null;
                ScheduleGeneratorDTO scheduleGeneratorDTO = this.loanUtilService.buildScheduleGeneratorDTO(loan, recalculateFrom);
                loan.regenerateRepaymentSchedule(scheduleGeneratorDTO);
            }
        }

        LoanDecision loanDecisionObj = loanDecisionAssembler.assembleIcReviewDecisionLevelThreeFrom(command, currentUser, loanDecision,
                Boolean.FALSE, icReviewOn, recommendedAmount, termFrequency, termPeriodFrequencyEnum);

        Integer nextStage = loanDecisionObj.getNextLoanIcReviewDecisionState();
        final AppUser nextApprover = getNextApprover(command, LoanDecisionState.fromInt(nextStage));
        setNextApprover(loanDecisionObj,nextStage,nextApprover);

        LoanDecision savedObj = loanDecisionRepository.saveAndFlush(loanDecisionObj);

        Loan loanObj = loan;
        loanObj.setLoanDecisionState(LoanDecisionState.IC_REVIEW_LEVEL_THREE.getValue());
        this.loanRepositoryWrapper.saveAndFlush(loanObj);

        Note note = null;
        if (StringUtils.isNotBlank(loanDecisionObj.getIcReviewDecisionLevelThreeNote())) {
            note = Note.loanNote(loanObj,
                    "Approve IC Review-Decision Level Three : " + loanDecisionObj.getIcReviewDecisionLevelThreeNote()
                            + " Recommended Amount : " + recommendedAmount + " " + loan.getCurrencyCode() + " Loan Term : " + termFrequency
                            + " " + PeriodFrequencyType.fromInt(termPeriodFrequencyEnum));
            this.noteRepository.save(note);
        }
        validateRecommendedAmountShouldNotBeGreaterThanProposedAmount(loan.getProposedPrincipal(), recommendedAmount);

        this.businessEventNotifierService.notifyPostBusinessEvent(
                new LoanDecisionAcceptedEvent(loan, savedObj, note));

        return new CommandProcessingResultBuilder() //
                .withCommandId(command.commandId()) //
                .withEntityId(savedObj.getId()) //
                .withOfficeId(loan.getOfficeId()) //
                .withClientId(loan.getClientId()) //
                .withGroupId(loan.getGroupId()) //
                .withLoanId(loanId) //
                .withResourceIdAsString(savedObj.getId().toString()).build();
    }

    @Override
    public CommandProcessingResult rejectIcReviewDecisionLevelThree(Long loanId, JsonCommand command) {
        final AppUser currentUser = getAppUserIfPresent();

        // Validate the current state
        final Loan loan = this.loanRepositoryWrapper.findOneWithNotFoundDetection(loanId, true);
        final LoanDecision loanDecision = this.loanDecisionRepository.findLoanDecisionByLoanId(loan.getId());

        if (!LoanDecisionState.fromInt(loan.getLoanDecisionState()).isIcReviewLevelThree()) {
            throw new GeneralPlatformDomainRuleException("error.msg.loan.decision.state.invalid.for.reject",
                    "Loan Decision state is invalid for reject operation. Expected IC_REVIEW_LEVEL_THREE.");
        }

        // Revert to the previous stage
        loan.setLoanDecisionState(LoanDecisionState.IC_REVIEW_LEVEL_TWO.getValue());
        loanDecision.setLoanDecisionState(LoanDecisionState.IC_REVIEW_LEVEL_TWO.getValue());
        loanDecision.setNextLoanIcReviewDecisionState(LoanDecisionState.IC_REVIEW_LEVEL_THREE.getValue());
        loanDecision.setRejectIcReviewDecisionLevelThreeSigned(true);

        Note note = null;
        final String noteText = command.stringValueOfParameterNamed("note");
        if (StringUtils.isNotBlank(noteText)) {
            note = Note.loanNote(loan, "Returned IC Review-Decision Level Three : " + noteText);
            this.noteRepository.save(note);
        }

        // Save changes
        this.loanRepositoryWrapper.saveAndFlush(loan);
        this.loanDecisionRepository.saveAndFlush(loanDecision);

        // Notify business event
        this.businessEventNotifierService.notifyPostBusinessEvent(new LoanDecisionRejectEvent(loan, loanDecision, note));

        return new CommandProcessingResultBuilder()
                .withCommandId(command.commandId())
                .withEntityId(loanDecision.getId())
                .withOfficeId(loan.getOfficeId())
                .withClientId(loan.getClientId())
                .withGroupId(loan.getGroupId())
                .withLoanId(loanId)
                .withResourceIdAsString(loanDecision.getId().toString())
                .build();
    }

    @Override
    public CommandProcessingResult acceptIcReviewDecisionLevelFour(Long loanId, JsonCommand command) {
        final AppUser currentUser = getAppUserIfPresent();

        this.loanDecisionTransitionApiJsonValidator.validateIcReviewStage(command.json());

        final Loan loan = this.loanRepositoryWrapper.findOneWithNotFoundDetection(loanId, true);
        final LoanDecision loanDecision = this.loanDecisionRepository.findLoanDecisionByLoanId(loan.getId());

        LocalDate icReviewOn = command.localDateValueOfParameterNamed(LoanApiConstants.icReviewOnDateParameterName);
        final BigDecimal recommendedAmount = command.bigDecimalValueOfParameterNamed(LoanApiConstants.icReviewRecommendedAmount);
        final Integer termFrequency = command.integerValueOfParameterNamed(LoanApiConstants.icReviewTermFrequency);
        final Integer termPeriodFrequencyEnum = command.integerValueOfParameterNamed(LoanApiConstants.icReviewTermPeriodFrequencyEnum);

        loanDecisionStateUtilService.validateIcReviewDecisionLevelFourBusinessRule(command, loan, loanDecision, icReviewOn);
        LoanApprovalMatrix approvalMatrix = this.loanApprovalMatrixRepository.findLoanApprovalMatrixByCurrency(loan.getCurrencyCode());

        if (approvalMatrix == null) {
            throw new GeneralPlatformDomainRuleException("error.msg.loan.approval.matrix.with.this.currency.does.not.exist.",
                    String.format("Loan Approval Matrix with Currency [ %s ] doesn't exist. Approval matrix is expected to continue ",
                            loan.getCurrencyCode()));
        }

        if (!loanDecision.getIdeaClient()) {
            final BigDecimal maxLoanAmountFromCashFlow = loanDecisionStateUtilService.getMaxLoanAmountFromCashFlow(loan);
            if (recommendedAmount.compareTo(maxLoanAmountFromCashFlow) > 0) {
                throw new GeneralPlatformDomainRuleException(
                        "error.msg.loan.ic.review.recommended.amount.can.not.greater.than.auto.computed.amount",
                        "Recommended amount can not be greater than auto-computed recommended amount", maxLoanAmountFromCashFlow);
            }
        }

        // Get Loan Matrix
        // Determine which cycle of this Loan Account
        // Determine the Next Level or stage to review
        // Add custom Params in Decision Table
        List<Loan> loanIndividualCounter = loanDecisionStateUtilService.getLoanCounter(loan);

        Boolean isLoanFirstCycle = loanDecisionStateUtilService.isLoanFirstCycle(loanIndividualCounter);
        Boolean isLoanUnsecure = loanDecisionStateUtilService.isLoanUnSecure(loan);
        final BigDecimal dueDiligenceRecommendedAmount = loanDecision.getDueDiligenceRecommendedAmount();

        loanDecisionStateUtilService.validateLoanAccountToComplyToApprovalMatrixStage(loan, approvalMatrix, isLoanFirstCycle,
                isLoanUnsecure, LoanDecisionState.IC_REVIEW_LEVEL_FOUR, dueDiligenceRecommendedAmount);
        // generate the next stage based on loan approval matrix via amounts to be disbursed
        loanDecisionStateUtilService.determineTheNextDecisionStage(loan, loanDecision, approvalMatrix, isLoanFirstCycle, isLoanUnsecure,
                LoanDecisionState.IC_REVIEW_LEVEL_FOUR, dueDiligenceRecommendedAmount);

        final Integer nextDecisionStage = loanDecision.getNextLoanIcReviewDecisionState();
        if (nextDecisionStage.equals(LoanDecisionState.PREPARE_AND_SIGN_CONTRACT.getValue())) {
            final Map<String, Object> changes = loan.loanApplicationICReview(currentUser, command);
            if (!changes.isEmpty()) {
                LocalDate recalculateFrom = null;
                ScheduleGeneratorDTO scheduleGeneratorDTO = this.loanUtilService.buildScheduleGeneratorDTO(loan, recalculateFrom);
                loan.regenerateRepaymentSchedule(scheduleGeneratorDTO);
            }
        }

        LoanDecision loanDecisionObj = loanDecisionAssembler.assembleIcReviewDecisionLevelFourFrom(command, currentUser, loanDecision,
                Boolean.FALSE, icReviewOn, recommendedAmount, termFrequency, termPeriodFrequencyEnum);

        Integer nextStage = loanDecisionObj.getNextLoanIcReviewDecisionState();
        final AppUser nextApprover = getNextApprover(command, LoanDecisionState.fromInt(nextStage));
        setNextApprover(loanDecisionObj,nextStage,nextApprover);

        LoanDecision savedObj = loanDecisionRepository.saveAndFlush(loanDecisionObj);

        Loan loanObj = loan;
        loanObj.setLoanDecisionState(LoanDecisionState.IC_REVIEW_LEVEL_FOUR.getValue());
        this.loanRepositoryWrapper.saveAndFlush(loanObj);

        Note note = null;
        if (StringUtils.isNotBlank(loanDecisionObj.getIcReviewDecisionLevelFourNote())) {
            note = Note.loanNote(loanObj,
                    "Approve IC Review-Decision Level Four : " + loanDecisionObj.getIcReviewDecisionLevelFourNote()
                            + " Recommended Amount : " + recommendedAmount + " " + loan.getCurrencyCode() + " Loan Term : " + termFrequency
                            + " " + PeriodFrequencyType.fromInt(termPeriodFrequencyEnum));
            this.noteRepository.save(note);
        }
        validateRecommendedAmountShouldNotBeGreaterThanProposedAmount(loan.getProposedPrincipal(), recommendedAmount);

        this.businessEventNotifierService.notifyPostBusinessEvent(
                new LoanDecisionAcceptedEvent(loan, savedObj, note));

        return new CommandProcessingResultBuilder() //
                .withCommandId(command.commandId()) //
                .withEntityId(savedObj.getId()) //
                .withOfficeId(loan.getOfficeId()) //
                .withClientId(loan.getClientId()) //
                .withGroupId(loan.getGroupId()) //
                .withLoanId(loanId) //
                .withResourceIdAsString(savedObj.getId().toString()).build();
    }

    @Override
    public CommandProcessingResult rejectIcReviewDecisionLevelFour(Long loanId, JsonCommand command) {
        final AppUser currentUser = getAppUserIfPresent();

        // Validate the current state
        final Loan loan = this.loanRepositoryWrapper.findOneWithNotFoundDetection(loanId, true);
        final LoanDecision loanDecision = this.loanDecisionRepository.findLoanDecisionByLoanId(loan.getId());

        if (!LoanDecisionState.fromInt(loan.getLoanDecisionState()).isIcReviewLevelFour()) {
            throw new GeneralPlatformDomainRuleException("error.msg.loan.decision.state.invalid.for.reject",
                    "Loan Decision state is invalid for reject operation. Expected IC_REVIEW_LEVEL_FOUR.");
        }

        // Revert to the previous stage
        loan.setLoanDecisionState(LoanDecisionState.IC_REVIEW_LEVEL_THREE.getValue());
        loanDecision.setLoanDecisionState(LoanDecisionState.IC_REVIEW_LEVEL_THREE.getValue());
        loanDecision.setNextLoanIcReviewDecisionState(LoanDecisionState.IC_REVIEW_LEVEL_FOUR.getValue());
        loanDecision.setRejectIcReviewDecisionLevelFourSigned(true);

        Note note = null;
        final String noteText = command.stringValueOfParameterNamed("note");
        if (StringUtils.isNotBlank(noteText)) {
            note = Note.loanNote(loan, "Reject IC Review-Decision Level Four : " + noteText);
            this.noteRepository.save(note);
        }

        // Save changes
        this.loanRepositoryWrapper.saveAndFlush(loan);
        this.loanDecisionRepository.saveAndFlush(loanDecision);

        // Notify business event
        this.businessEventNotifierService.notifyPostBusinessEvent(new LoanDecisionRejectEvent(loan, loanDecision, note));

        return new CommandProcessingResultBuilder()
                .withCommandId(command.commandId())
                .withEntityId(loanDecision.getId())
                .withOfficeId(loan.getOfficeId())
                .withClientId(loan.getClientId())
                .withGroupId(loan.getGroupId())
                .withLoanId(loanId)
                .withResourceIdAsString(loanDecision.getId().toString())
                .build();
    }

    @Override
    public CommandProcessingResult acceptIcReviewDecisionLevelFive(Long loanId, JsonCommand command) {
        final AppUser currentUser = getAppUserIfPresent();

        this.loanDecisionTransitionApiJsonValidator.validateIcReviewStage(command.json());

        final Loan loan = this.loanRepositoryWrapper.findOneWithNotFoundDetection(loanId, true);
        final LoanDecision loanDecision = this.loanDecisionRepository.findLoanDecisionByLoanId(loan.getId());

        LocalDate icReviewOn = command.localDateValueOfParameterNamed(LoanApiConstants.icReviewOnDateParameterName);
        final BigDecimal recommendedAmount = command.bigDecimalValueOfParameterNamed(LoanApiConstants.icReviewRecommendedAmount);
        final Integer termFrequency = command.integerValueOfParameterNamed(LoanApiConstants.icReviewTermFrequency);
        final Integer termPeriodFrequencyEnum = command.integerValueOfParameterNamed(LoanApiConstants.icReviewTermPeriodFrequencyEnum);

        loanDecisionStateUtilService.validateIcReviewDecisionLevelFiveBusinessRule(command, loan, loanDecision, icReviewOn);
        LoanApprovalMatrix approvalMatrix = this.loanApprovalMatrixRepository.findLoanApprovalMatrixByCurrency(loan.getCurrencyCode());

        if (approvalMatrix == null) {
            throw new GeneralPlatformDomainRuleException("error.msg.loan.approval.matrix.with.this.currency.does.not.exist.",
                    String.format("Loan Approval Matrix with Currency [ %s ] doesn't exist. Approval matrix is expected to continue ",
                            loan.getCurrencyCode()));
        }

        if (!loanDecision.getIdeaClient()) {
            final BigDecimal maxLoanAmountFromCashFlow = loanDecisionStateUtilService.getMaxLoanAmountFromCashFlow(loan);
            if (recommendedAmount.compareTo(maxLoanAmountFromCashFlow) > 0) {
                throw new GeneralPlatformDomainRuleException(
                        "error.msg.loan.ic.review.recommended.amount.can.not.greater.than.auto.computed.amount",
                        "Recommended amount can not be greater than auto-computed recommended amount", maxLoanAmountFromCashFlow);
            }
        }

        // Get Loan Matrix
        // Determine which cycle of this Loan Account
        // Determine the Next Level or stage to review
        // Add custom Params in Decision Table
        List<Loan> loanIndividualCounter = loanDecisionStateUtilService.getLoanCounter(loan);

        Boolean isLoanFirstCycle = loanDecisionStateUtilService.isLoanFirstCycle(loanIndividualCounter);
        Boolean isLoanUnsecure = loanDecisionStateUtilService.isLoanUnSecure(loan);
        final BigDecimal dueDiligenceRecommendedAmount = loanDecision.getDueDiligenceRecommendedAmount();

        loanDecisionStateUtilService.validateLoanAccountToComplyToApprovalMatrixStage(loan, approvalMatrix, isLoanFirstCycle,
                isLoanUnsecure, LoanDecisionState.IC_REVIEW_LEVEL_FIVE, dueDiligenceRecommendedAmount);

        // Determine the next decision stage BEFORE assembling (this will check if Level 6+ exists)
        loanDecisionStateUtilService.determineTheNextDecisionStage(loan, loanDecision, approvalMatrix, isLoanFirstCycle, isLoanUnsecure,
                LoanDecisionState.IC_REVIEW_LEVEL_FIVE, dueDiligenceRecommendedAmount);

        final Integer nextDecisionStage = loanDecision.getNextLoanIcReviewDecisionState();
        if (nextDecisionStage.equals(LoanDecisionState.PREPARE_AND_SIGN_CONTRACT.getValue())) {
            final Map<String, Object> changes = loan.loanApplicationICReview(currentUser, command);
            if (!changes.isEmpty()) {
                LocalDate recalculateFrom = null;
                ScheduleGeneratorDTO scheduleGeneratorDTO = this.loanUtilService.buildScheduleGeneratorDTO(loan, recalculateFrom);
                loan.regenerateRepaymentSchedule(scheduleGeneratorDTO);
            }
        }

        LoanDecision loanDecisionObj = loanDecisionAssembler.assembleIcReviewDecisionLevelFiveFrom(command, currentUser, loanDecision,
                Boolean.FALSE, icReviewOn, recommendedAmount, termFrequency, termPeriodFrequencyEnum);

        // Use the next stage determined dynamically (may be Level 6+ or PREPARE_AND_SIGN_CONTRACT)
        Integer nextStage = nextDecisionStage;
        final AppUser nextApprover = getNextApprover(command, LoanDecisionState.fromInt(nextStage));
        setNextApprover(loanDecisionObj,nextStage,nextApprover);

        LoanDecision savedObj = loanDecisionRepository.saveAndFlush(loanDecisionObj);

        Loan loanObj = loan;
        loanObj.setLoanDecisionState(LoanDecisionState.IC_REVIEW_LEVEL_FIVE.getValue());
        this.loanRepositoryWrapper.saveAndFlush(loanObj);

        Note note = null;
        if (StringUtils.isNotBlank(loanDecisionObj.getIcReviewDecisionLevelFiveNote())) {
            note = Note.loanNote(loanObj,
                    "Approve IC Review-Decision Level Five : " + loanDecisionObj.getIcReviewDecisionLevelFiveNote()
                            + " Recommended Amount : " + recommendedAmount + " " + loan.getCurrencyCode() + " Loan Term : " + termFrequency
                            + " " + PeriodFrequencyType.fromInt(termPeriodFrequencyEnum));
            this.noteRepository.save(note);
        }
        validateRecommendedAmountShouldNotBeGreaterThanProposedAmount(loan.getProposedPrincipal(), recommendedAmount);

        this.businessEventNotifierService.notifyPostBusinessEvent(
                new LoanDecisionAcceptedEvent(loan, savedObj, note));

        return new CommandProcessingResultBuilder() //
                .withCommandId(command.commandId()) //
                .withEntityId(savedObj.getId()) //
                .withOfficeId(loan.getOfficeId()) //
                .withClientId(loan.getClientId()) //
                .withGroupId(loan.getGroupId()) //
                .withLoanId(loanId) //
                .withResourceIdAsString(savedObj.getId().toString()).build();
    }

    @Override
    public CommandProcessingResult rejectIcReviewDecisionLevelFive(Long loanId, JsonCommand command) {
        final AppUser currentUser = getAppUserIfPresent();

        // Validate the current state
        final Loan loan = this.loanRepositoryWrapper.findOneWithNotFoundDetection(loanId, true);
        final LoanDecision loanDecision = this.loanDecisionRepository.findLoanDecisionByLoanId(loan.getId());

        if (!LoanDecisionState.fromInt(loan.getLoanDecisionState()).isIcReviewLevelFive()) {
            throw new GeneralPlatformDomainRuleException("error.msg.loan.decision.state.invalid.for.reject",
                    "Loan Decision state is invalid for reject operation. Expected IC_REVIEW_LEVEL_FIVE.");
        }

        // Revert to the previous stage
        loan.setLoanDecisionState(LoanDecisionState.IC_REVIEW_LEVEL_FOUR.getValue());
        loanDecision.setLoanDecisionState(LoanDecisionState.IC_REVIEW_LEVEL_FOUR.getValue());
        loanDecision.setNextLoanIcReviewDecisionState(LoanDecisionState.IC_REVIEW_LEVEL_FIVE.getValue());
        loanDecision.setRejectIcReviewDecisionLevelFiveSigned(true);

        Note note = null;
        final String noteText = command.stringValueOfParameterNamed("note");
        if (StringUtils.isNotBlank(noteText)) {
            note = Note.loanNote(loan, "Returned IC Review-Decision Level Five : " + noteText);
            this.noteRepository.save(note);
        }

        // Save changes
        this.loanRepositoryWrapper.saveAndFlush(loan);
        this.loanDecisionRepository.saveAndFlush(loanDecision);

        // Notify business event
        this.businessEventNotifierService.notifyPostBusinessEvent(new LoanDecisionRejectEvent(loan, loanDecision, note));

        return new CommandProcessingResultBuilder()
                .withCommandId(command.commandId())
                .withEntityId(loanDecision.getId())
                .withOfficeId(loan.getOfficeId())
                .withClientId(loan.getClientId())
                .withGroupId(loan.getGroupId())
                .withLoanId(loanId)
                .withResourceIdAsString(loanDecision.getId().toString())
                .build();
    }

    @Override
    public CommandProcessingResult acceptPrepareAndSignContract(Long loanId, JsonCommand command) {
        final AppUser currentUser = getAppUserIfPresent();

        final Collection<DocumentData> documentData = this.documentReadPlatformService.retrieveLoanDocumentsFilterByDocumentType(
                LoanApiConstants.loanEntityType, loanId, LoanApiConstants.loanDocumentTypeContract);
        if (documentData.size() < 1) {
            throw new GeneralPlatformDomainRuleException("error.msg.loan.document.with.type.contract.not.found",
                    "Loan contract document not found. Please upload loan document with type Contract");
        }

        this.loanDecisionTransitionApiJsonValidator.validatePrepareAndSignContractStage(command.json());

        final Loan loan = this.loanRepositoryWrapper.findOneWithNotFoundDetection(loanId, true);
        final LoanDecision loanDecision = this.loanDecisionRepository.findLoanDecisionByLoanId(loan.getId());

        loanDecisionStateUtilService.validatePrepareAndSignContractBusinessRule(command, loan, loanDecision);
        LoanApprovalMatrix approvalMatrix = this.loanApprovalMatrixRepository.findLoanApprovalMatrixByCurrency(loan.getCurrencyCode());

        if (approvalMatrix == null) {
            throw new GeneralPlatformDomainRuleException("error.msg.loan.approval.matrix.with.this.currency.does.not.exist.",
                    String.format("Loan Approval Matrix with Currency [ %s ] doesn't exist. Approval matrix is expected to continue ",
                            loan.getCurrencyCode()));
        }

        LoanDecision loanDecisionObj = loanDecisionAssembler.assemblePrepareAndSignContractFrom(command, currentUser, loanDecision);
        LoanDecision savedObj = loanDecisionRepository.saveAndFlush(loanDecisionObj);

        savedObj.setPreviousLoanIcReviewDecisionState(loan.getLoanDecisionState());

        Loan loanObj = loan;
        loanObj.setLoanDecisionState(LoanDecisionState.PREPARE_AND_SIGN_CONTRACT.getValue());
        this.loanRepositoryWrapper.saveAndFlush(loanObj);

        Note note;
        if (StringUtils.isNotBlank(loanDecisionObj.getPrepareAndSignContractNote())) {
            note = Note.loanNote(loanObj, "Prepare And Sign Contract : " + loanDecisionObj.getPrepareAndSignContractNote());
            this.noteRepository.save(note);
        }

        return new CommandProcessingResultBuilder() //
                .withCommandId(command.commandId()) //
                .withEntityId(savedObj.getId()) //
                .withOfficeId(loan.getOfficeId()) //
                .withClientId(loan.getClientId()) //
                .withGroupId(loan.getGroupId()) //
                .withLoanId(loanId) //
                .withResourceIdAsString(savedObj.getId().toString()).build();
    }

    @Override
    public CommandProcessingResult rejectPrepareAndSignContract(Long loanId, JsonCommand command) {
        final AppUser currentUser = getAppUserIfPresent();

        // Validate the current state
        final Loan loan = this.loanRepositoryWrapper.findOneWithNotFoundDetection(loanId, true);
        final LoanDecision loanDecision = this.loanDecisionRepository.findLoanDecisionByLoanId(loan.getId());

        if (!LoanDecisionState.fromInt(loan.getLoanDecisionState()).isPrepareAndSignContract()) {
            throw new GeneralPlatformDomainRuleException("error.msg.loan.decision.state.invalid.for.reject",
                    "Loan Decision state is invalid for reject operation. Expected PREPARE_AND_SIGN_CONTRACT.");
        }

        // Revert to the previous stage
        Integer previousLoanDecisionState = loanDecision.getPreviousLoanIcReviewDecisionState();

        loan.setLoanDecisionState(previousLoanDecisionState);
        loanDecision.setLoanDecisionState(previousLoanDecisionState);
        loanDecision.setNextLoanIcReviewDecisionState(LoanDecisionState.PREPARE_AND_SIGN_CONTRACT.getValue());
        loanDecision.setRejectPrepareAndSignContractSigned(true);

        Note note = null;
        final String noteText = command.stringValueOfParameterNamed("note");
        if (StringUtils.isNotBlank(noteText)) {
            note = Note.loanNote(loan, "Returned Prepare and Sign Contract : " + noteText);
            this.noteRepository.save(note);
        }

        // Save changes
        this.loanRepositoryWrapper.saveAndFlush(loan);
        this.loanDecisionRepository.saveAndFlush(loanDecision);

        // Notify business event
        this.businessEventNotifierService.notifyPostBusinessEvent(new LoanDecisionRejectEvent(loan, loanDecision, note));

        return new CommandProcessingResultBuilder()
                .withCommandId(command.commandId())
                .withEntityId(loanDecision.getId())
                .withOfficeId(loan.getOfficeId())
                .withClientId(loan.getClientId())
                .withGroupId(loan.getGroupId())
                .withLoanId(loanId)
                .withResourceIdAsString(loanDecision.getId().toString())
                .build();
    }

    private void validateDueDiligenceBusinessRule(JsonCommand command, Loan loan, LoanDecision loanDecision) {
        Boolean isExtendLoanLifeCycleConfig = loanDecisionStateUtilService.getExtendLoanLifeCycleConfig().isEnabled();

        if (!isExtendLoanLifeCycleConfig) {
            throw new GeneralPlatformDomainRuleException("error.msg.Add-More-Stages-To-A-Loan-Life-Cycle.is.not.set",
                    "Add-More-Stages-To-A-Loan-Life-Cycle settings is not set. So this operation is not permitted");
        }

        if (loanDecision == null) {
            throw new GeneralPlatformDomainRuleException("error.msg.loan.account.should.not.found.in.decision.engine",
                    "Loan Account not found in decision engine. Operation [Due Diligence] is not allowed");
        }
        loanDecisionStateUtilService.checkClientOrGroupActive(loan);

        loanDecisionStateUtilService.validateLoanDisbursementDataWithMeetingDate(loan);
        loanDecisionStateUtilService.validateLoanTopUp(loan);
        LocalDate dueDiligenceOn = command.localDateValueOfParameterNamed(LoanApiConstants.dueDiligenceOnDateParameterName);
        // Review Loan Application should not be before Due Diligence date
        if (dueDiligenceOn.isBefore(loanDecision.getReviewApplicationOn())) {
            throw new GeneralPlatformDomainRuleException("error.msg.loan.due.diligence.date.should.be.after.review.application.date",
                    "Approve Due Diligence date" + dueDiligenceOn + " should be after Loan Review Application date "
                            + loanDecision.getReviewApplicationOn());
        }
        // Due Diligence date should not be before loan submission date
        if (dueDiligenceOn.isBefore(loan.getSubmittedOnDate())) {
            throw new GeneralPlatformDomainRuleException("error.msg.loan.review.application.date.should.be.after.submission.date",
                    "Approve Due Diligence date " + dueDiligenceOn + " should be after Loan submission date " + loan.getSubmittedOnDate());
        }

        if (!loan.status().isSubmittedAndPendingApproval()) {
            throw new GeneralPlatformDomainRuleException("error.msg.loan.current.status.is.invalid",
                    "Loan Account current status is invalid. Expected" + loan.status().getCode() + " but found " + loan.status().getCode());
        }
        if (!LoanDecisionState.fromInt(loan.getLoanDecisionState()).isReviewApplication()) {
            throw new GeneralPlatformDomainRuleException("error.msg.loan.decision.state.is.invalid",
                    "Loan Account Decision state is invalid. Expected" + LoanDecisionState.REVIEW_APPLICATION.getValue() + " but found "
                            + loan.getLoanDecisionState());
        }
        if (!loan.getLoanDecisionState().equals(loanDecision.getLoanDecisionState())) {
            throw new GeneralPlatformDomainRuleException("error.msg.loan.decision.state.does.not.reconcile",
                    "Loan Account Decision state Does not reconcile . Operation is terminated");
        }

    }

    private void validateReviewApplicationBusinessRule(JsonCommand command, Loan loan, LoanDecision loanDecision) {
        Boolean isExtendLoanLifeCycleConfig = loanDecisionStateUtilService.getExtendLoanLifeCycleConfig().isEnabled();

        if (!isExtendLoanLifeCycleConfig) {
            throw new GeneralPlatformDomainRuleException("error.msg.Add-More-Stages-To-A-Loan-Life-Cycle.is.not.set",
                    "Add-More-Stages-To-A-Loan-Life-Cycle settings is not set. So this operation is not permitted");
        }

        if (loanDecision != null) {
            throw new GeneralPlatformDomainRuleException("error.msg.loan.account.should.not.exist.in.decision.engine",
                    "Loan Account found in decision engine. Operation [Review Application] is not allowed");
        }
        loanDecisionStateUtilService.checkClientOrGroupActive(loan);

        loanDecisionStateUtilService.validateLoanDisbursementDataWithMeetingDate(loan);
        loanDecisionStateUtilService.validateLoanTopUp(loan);

        LocalDate loanReviewOnDate = command.localDateValueOfParameterNamed(LoanApiConstants.loanReviewOnDateParameterName);
        if (loanReviewOnDate.isBefore(loan.getSubmittedOnDate())) {
            throw new GeneralPlatformDomainRuleException("error.msg.loan.review.application.date.should.be.after.submission.date",
                    "Loan Review Application date " + loanReviewOnDate + " should be after submission date " + loan.getSubmittedOnDate());
        }

        if (!loan.status().isSubmittedAndPendingApproval()) {
            throw new GeneralPlatformDomainRuleException("error.msg.loan.current.status.is.invalid",
                    "Loan Account current status is invalid. Expected" + loan.status().getCode() + " but found " + loan.status().getCode());
        }
    }

    private static void validateRecommendedAmountShouldNotBeGreaterThanProposedAmount(BigDecimal proposedAmount, BigDecimal recommendedAmount) {
        if (recommendedAmount.compareTo(proposedAmount) > 0) {
            throw new PlatformDataIntegrityException("error.msg.loan.recommended.amount.cannot.be.greater.than.applied.amount",
                    "Recommended amount cannot be greater than the Applied amount", proposedAmount);
        }
    }

    private AppUser getAppUserIfPresent() {
        AppUser user = null;
        if (this.context != null) {
            user = this.context.getAuthenticatedUserIfPresent();
        }
        return user;
    }

    private AppUser getNextApprover(JsonCommand command, LoanDecisionState nextStage) {
        final Long nextApproverUserId = command.longValueOfParameterNamed("nextApproverUserId");
        if (nextApproverUserId != null && !nextStage.equals(LoanDecisionState.PREPARE_AND_SIGN_CONTRACT)) {
            return appUserRepository.findById(nextApproverUserId)
                    .orElseThrow(() -> new GeneralPlatformDomainRuleException("validation.msg.next.approver.user.id.invalid",
                            "Next approver user not found for ID: " + nextApproverUserId));
        } else if (nextApproverUserId == null && !nextStage.equals(LoanDecisionState.PREPARE_AND_SIGN_CONTRACT) ){
            throw new GeneralPlatformDomainRuleException("error.msg.loan.next.approver.user.id.required",
                    "The field 'nextApproverUserId' is required.");
        }else {
            final Loan loan = this.loanRepositoryWrapper.findOneWithNotFoundDetection(command.getLoanId(), true);
            Staff loanOfficer =loan.getLoanOfficer();
            if (loanOfficer == null)
                throw new GeneralPlatformDomainRuleException("error.msg.loan.does.not.have.officer",
                        "The loan is missing a loan officer");
        }
        return null;
    }

    private void setNextApprover(LoanDecision decision, Integer nextStage, AppUser nextApprover) {
        LoanDecisionState state = LoanDecisionState.fromInt(nextStage);

        // Handle non-IC review states
        switch (state) {
            case DUE_DILIGENCE -> decision.setDueDiligenceBy(nextApprover);
            case PREPARE_AND_SIGN_CONTRACT -> decision.setPrepareAndSignContractBy(nextApprover);
            case REVIEW_APPLICATION, COLLATERAL_REVIEW, INVALID -> {}
            default -> {
                // Handle IC review levels (1-5 legacy + 6+ dynamic)
                if (dynamicIcReviewLevelHelper.isIcReviewLevel(nextStage)) {
                    Integer levelNumber = dynamicIcReviewLevelHelper.getIcReviewLevelNumber(nextStage);

                    if (levelNumber != null) {
                        // Update legacy fields for levels 1-5 (backward compatibility)
                        if (levelNumber >= 1 && levelNumber <= 5) {
                            setLegacyApprover(decision, levelNumber, nextApprover);
                        }

                        // Update dynamic level (for all levels including 6+)
                        setDynamicApprover(decision, levelNumber, nextApprover);
                    }
                }
            }
        }
    }

    /**
     * Set approver in legacy fields for backward compatibility (levels 1-5 only)
     */
    private void setLegacyApprover(LoanDecision decision, Integer levelNumber, AppUser approver) {
        switch (levelNumber) {
            case 1 -> decision.setIcReviewDecisionLevelOneBy(approver);
            case 2 -> decision.setIcReviewDecisionLevelTwoBy(approver);
            case 3 -> decision.setIcReviewDecisionLevelThreeBy(approver);
            case 4 -> decision.setIcReviewDecisionLevelFourBy(approver);
            case 5 -> decision.setIcReviewDecisionLevelFiveBy(approver);
        }
    }

    /**
     * Set approver in dynamic LoanDecisionLevel entity (for all levels including 6+)
     */
    private void setDynamicApprover(LoanDecision decision, Integer levelNumber, AppUser approver) {
        // Query database first instead of relying on lazy-loaded in-memory collection
        LoanDecisionLevel level = loanDecisionLevelRepository
                .findByLoanDecisionIdAndLevelNumber(decision.getId(), levelNumber);

        if (level == null) {
            // Create new level if it doesn't exist
            IcReviewLevelConfig levelConfig = icReviewLevelConfigRepository.findByLevelNumberAndActive(levelNumber);
            if (levelConfig == null) {
                log.warn("IC Review Level {} not found in configuration", levelNumber);
                return;
            }

            level = new LoanDecisionLevel();
            level.setLoanDecision(decision);
            level.setIcReviewLevel(levelConfig);
            level.setLevelNumber(levelNumber);
            level.setIsSigned(Boolean.FALSE);
            level.setIsRejected(Boolean.FALSE);
        }

        level.setDecisionBy(approver);
        loanDecisionLevelRepository.save(level);
        log.debug("Set approver for IC Review Level {}: {}", levelNumber, approver.getUsername());
    }

    /**
     * Dynamic IC Review Decision Accept - supports unlimited levels
     * This method handles IC review decisions for any level number (1, 2, 3, 4, 5, 6, 7, ...)
     */
    @Override
    public CommandProcessingResult acceptIcReviewDecisionDynamic(Long loanId, JsonCommand command, Integer levelNumber) {
        final AppUser currentUser = getAppUserIfPresent();

        this.loanDecisionTransitionApiJsonValidator.validateIcReviewStage(command.json());

        final Loan loan = this.loanRepositoryWrapper.findOneWithNotFoundDetection(loanId, true);
        final LoanDecision loanDecision = this.loanDecisionRepository.findLoanDecisionByLoanId(loan.getId());

        // Get the IC review level configuration
        IcReviewLevelConfig levelConfig = icReviewLevelConfigRepository.findByLevelNumberAndActive(levelNumber);
        if (levelConfig == null) {
            throw new GeneralPlatformDomainRuleException("error.msg.ic.review.level.not.found",
                    String.format("IC Review Level %d is not configured or not active", levelNumber));
        }

        LocalDate icReviewOn = command.localDateValueOfParameterNamed(LoanApiConstants.icReviewOnDateParameterName);
        final BigDecimal recommendedAmount = command.bigDecimalValueOfParameterNamed(LoanApiConstants.icReviewRecommendedAmount);
        final Integer termFrequency = command.integerValueOfParameterNamed(LoanApiConstants.icReviewTermFrequency);
        final Integer termPeriodFrequencyEnum = command.integerValueOfParameterNamed(LoanApiConstants.icReviewTermPeriodFrequencyEnum);

        // Validate business rules based on level
        validateIcReviewDecisionBusinessRule(command, loan, loanDecision, icReviewOn, levelNumber, levelConfig);

        LoanApprovalMatrix approvalMatrix = this.loanApprovalMatrixRepository.findLoanApprovalMatrixByCurrency(loan.getCurrencyCode());

        if (approvalMatrix == null) {
            throw new GeneralPlatformDomainRuleException("error.msg.loan.approval.matrix.with.this.currency.does.not.exist.",
                    String.format("Loan Approval Matrix with Currency [ %s ] doesn't exist. Approval matrix is expected to continue ",
                            loan.getCurrencyCode()));
        }

        if (!loanDecision.getIdeaClient()) {
            final BigDecimal maxLoanAmountFromCashFlow = loanDecisionStateUtilService.getMaxLoanAmountFromCashFlow(loan);
            if (recommendedAmount.compareTo(maxLoanAmountFromCashFlow) > 0) {
                throw new GeneralPlatformDomainRuleException(
                        "error.msg.loan.ic.review.recommended.amount.can.not.greater.than.auto.computed.amount",
                        "Recommended amount can not be greater than auto-computed recommended amount", maxLoanAmountFromCashFlow);
            }
        }

        // Get Loan Matrix and determine cycle
        List<Loan> loanIndividualCounter = loanDecisionStateUtilService.getLoanCounter(loan);
        Boolean isLoanFirstCycle = loanDecisionStateUtilService.isLoanFirstCycle(loanIndividualCounter);
        Boolean isLoanUnsecure = loanDecisionStateUtilService.isLoanUnSecure(loan);
        final BigDecimal dueDiligenceRecommendedAmount = loanDecision.getDueDiligenceRecommendedAmount();

        // Validate against approval matrix for this level
        LoanDecisionState currentLevelState = LoanDecisionState.fromInt(levelConfig.getDecisionStateValue());
        loanDecisionStateUtilService.validateLoanAccountToComplyToApprovalMatrixStage(loan, approvalMatrix, isLoanFirstCycle,
                isLoanUnsecure, currentLevelState, dueDiligenceRecommendedAmount);

        // Determine the next decision stage
        loanDecisionStateUtilService.determineTheNextDecisionStage(loan, loanDecision, approvalMatrix, isLoanFirstCycle, isLoanUnsecure,
                currentLevelState, dueDiligenceRecommendedAmount);

        final Integer nextDecisionStage = loanDecision.getNextLoanIcReviewDecisionState();
        if (nextDecisionStage.equals(LoanDecisionState.PREPARE_AND_SIGN_CONTRACT.getValue())) {
            final Map<String, Object> changes = loan.loanApplicationICReview(currentUser, command);
            if (!changes.isEmpty()) {
                LocalDate recalculateFrom = null;
                ScheduleGeneratorDTO scheduleGeneratorDTO = this.loanUtilService.buildScheduleGeneratorDTO(loan, recalculateFrom);
                loan.regenerateRepaymentSchedule(scheduleGeneratorDTO);
            }
        }

        // Save decision data in the dynamic table - check for existing record first
        LoanDecisionLevel decisionLevel = loanDecisionLevelRepository
                .findByLoanDecisionIdAndLevelNumber(loanDecision.getId(), levelNumber);

        if (decisionLevel == null) {
            decisionLevel = new LoanDecisionLevel();
            decisionLevel.setLoanDecision(loanDecision);
            decisionLevel.setIcReviewLevel(levelConfig);
            decisionLevel.setLevelNumber(levelNumber);
        }

        decisionLevel.setNote(command.stringValueOfParameterNamed("note"));
        decisionLevel.setIsSigned(true);
        decisionLevel.setIsRejected(false);
        decisionLevel.setDecisionOn(icReviewOn);
        decisionLevel.setDecisionBy(currentUser);
        decisionLevel.setRecommendedAmount(recommendedAmount);
        decisionLevel.setTermFrequency(termFrequency);
        decisionLevel.setTermPeriodFrequencyEnum(termPeriodFrequencyEnum);

        loanDecisionLevelRepository.save(decisionLevel);

        // Also update the legacy fields for backward compatibility (levels 1-5)
        if (levelNumber <= 5) {
            updateLegacyIcReviewFields(loanDecision, levelNumber, command, currentUser, icReviewOn,
                    recommendedAmount, termFrequency, termPeriodFrequencyEnum, false);
        }

        Integer nextStage = loanDecision.getNextLoanIcReviewDecisionState();
        final AppUser nextApprover = getNextApprover(command, LoanDecisionState.fromInt(nextStage));
        setNextApproverDynamic(loanDecision, nextStage, nextApprover);

        LoanDecision savedObj = loanDecisionRepository.saveAndFlush(loanDecision);

        Loan loanObj = loan;
        loanObj.setLoanDecisionState(levelConfig.getDecisionStateValue());
        this.loanRepositoryWrapper.saveAndFlush(loanObj);

        Note note = null;
        if (StringUtils.isNotBlank(decisionLevel.getNote())) {
            note = Note.loanNote(loanObj,
                    "Approve IC Review-Decision Level " + levelConfig.getLevelName() + " : " + decisionLevel.getNote()
                            + " Recommended Amount : " + recommendedAmount + " " + loan.getCurrencyCode()
                            + " Loan Term : " + termFrequency + " " + PeriodFrequencyType.fromInt(termPeriodFrequencyEnum));
            this.noteRepository.save(note);
        }
        validateRecommendedAmountShouldNotBeGreaterThanProposedAmount(loan.getProposedPrincipal(), recommendedAmount);

        this.businessEventNotifierService.notifyPostBusinessEvent(
                new LoanDecisionAcceptedEvent(loan, savedObj, note));

        return new CommandProcessingResultBuilder() //
                .withCommandId(command.commandId()) //
                .withEntityId(savedObj.getId()) //
                .withOfficeId(loan.getOfficeId()) //
                .withClientId(loan.getClientId()) //
                .withGroupId(loan.getGroupId()) //
                .withLoanId(loanId) //
                .withResourceIdAsString(savedObj.getId().toString()).build();
    }

    /**
     * Dynamic IC Review Decision Reject - supports unlimited levels
     * This method handles IC review decision rejections for any level number (1, 2, 3, 4, 5, 6, 7, ...)
     */
    @Override
    public CommandProcessingResult rejectIcReviewDecisionDynamic(Long loanId, JsonCommand command, Integer levelNumber) {
        final AppUser currentUser = getAppUserIfPresent();

        // Get the IC review level configuration
        IcReviewLevelConfig levelConfig = icReviewLevelConfigRepository.findByLevelNumberAndActive(levelNumber);
        if (levelConfig == null) {
            throw new GeneralPlatformDomainRuleException("error.msg.ic.review.level.not.found",
                    String.format("IC Review Level %d is not configured or not active", levelNumber));
        }

        // Validate the current state
        final Loan loan = this.loanRepositoryWrapper.findOneWithNotFoundDetection(loanId, true);
        final LoanDecision loanDecision = this.loanDecisionRepository.findLoanDecisionByLoanId(loan.getId());

        if (!loan.getLoanDecisionState().equals(levelConfig.getDecisionStateValue())) {
            throw new GeneralPlatformDomainRuleException("error.msg.loan.decision.state.invalid.for.reject",
                    String.format("Loan Decision state is invalid for reject operation. Expected IC_REVIEW_LEVEL_%s.",
                            levelConfig.getLevelName()));
        }

        // Determine previous stage
        Integer previousState = dynamicIcReviewLevelHelper.getPreviousIcReviewDecisionState(levelConfig.getDecisionStateValue());
        if (previousState == null) {
            // If no previous IC review level, revert to DUE_DILIGENCE
            previousState = LoanDecisionState.DUE_DILIGENCE.getValue();
        }

        // Revert to the previous stage
        loan.setLoanDecisionState(previousState);
        loanDecision.setLoanDecisionState(previousState);
        loanDecision.setNextLoanIcReviewDecisionState(levelConfig.getDecisionStateValue());

        // Save rejection in dynamic table - check for existing record first
        LoanDecisionLevel decisionLevel = loanDecisionLevelRepository
                .findByLoanDecisionIdAndLevelNumber(loanDecision.getId(), levelNumber);

        if (decisionLevel == null) {
            decisionLevel = new LoanDecisionLevel();
            decisionLevel.setLoanDecision(loanDecision);
            decisionLevel.setIcReviewLevel(levelConfig);
            decisionLevel.setLevelNumber(levelNumber);
        }

        decisionLevel.setNote(command.stringValueOfParameterNamed("note"));
        decisionLevel.setIsSigned(false);
        decisionLevel.setIsRejected(true);
        decisionLevel.setDecisionOn(LocalDate.now(ZoneId.systemDefault()));
        decisionLevel.setDecisionBy(currentUser);

        loanDecisionLevelRepository.save(decisionLevel);

        // Also update the legacy fields for backward compatibility (levels 1-5)
        if (levelNumber <= 5) {
            updateLegacyIcReviewRejectFields(loanDecision, levelNumber);
        }

        Note note = null;
        final String noteText = command.stringValueOfParameterNamed("note");
        if (StringUtils.isNotBlank(noteText)) {
            note = Note.loanNote(loan, "Returned IC Review-Decision Level " + levelConfig.getLevelName() + " : " + noteText);
            this.noteRepository.save(note);
        }

        // Save changes
        this.loanRepositoryWrapper.saveAndFlush(loan);
        this.loanDecisionRepository.saveAndFlush(loanDecision);

        // Notify business event
        this.businessEventNotifierService.notifyPostBusinessEvent(new LoanDecisionRejectEvent(loan, loanDecision, note));

        return new CommandProcessingResultBuilder()
                .withCommandId(command.commandId())
                .withEntityId(loanDecision.getId())
                .withOfficeId(loan.getOfficeId())
                .withClientId(loan.getClientId())
                .withGroupId(loan.getGroupId())
                .withLoanId(loanId)
                .withResourceIdAsString(loanDecision.getId().toString())
                .build();
    }

    /**
     * Validate IC review decision business rules for a specific level
     */
    private void validateIcReviewDecisionBusinessRule(JsonCommand command, Loan loan, LoanDecision loanDecision,
            LocalDate icReviewOn, Integer levelNumber, IcReviewLevelConfig levelConfig) {

        Boolean isExtendLoanLifeCycleConfig = loanDecisionStateUtilService.getExtendLoanLifeCycleConfig().isEnabled();

        if (!isExtendLoanLifeCycleConfig) {
            throw new GeneralPlatformDomainRuleException("error.msg.Add-More-Stages-To-A-Loan-Life-Cycle.is.not.set",
                    "Add-More-Stages-To-A-Loan-Life-Cycle settings is not set. So this operation is not permitted");
        }

        if (loanDecision == null) {
            throw new GeneralPlatformDomainRuleException("error.msg.loan.account.should.not.found.in.decision.engine",
                    "Loan Account not found in decision engine. Operation [IC Review Level " + levelNumber + "] is not allowed");
        }

        loanDecisionStateUtilService.checkClientOrGroupActive(loan);
        loanDecisionStateUtilService.validateLoanDisbursementDataWithMeetingDate(loan);
        loanDecisionStateUtilService.validateLoanTopUp(loan);

        // Validate date is after previous stage
        LocalDate previousStageDate = getPreviousStageDate(loanDecision, levelNumber);
        if (previousStageDate != null && icReviewOn.isBefore(previousStageDate)) {
            throw new GeneralPlatformDomainRuleException("error.msg.loan.ic.review.date.should.be.after.previous.stage.date",
                    "IC Review Level " + levelNumber + " date " + icReviewOn + " should be after previous stage date " + previousStageDate);
        }

        // IC Review date should not be before loan submission date
        if (icReviewOn.isBefore(loan.getSubmittedOnDate())) {
            throw new GeneralPlatformDomainRuleException("error.msg.loan.ic.review.date.should.be.after.submission.date",
                    "IC Review Level " + levelNumber + " date " + icReviewOn + " should be after Loan submission date " + loan.getSubmittedOnDate());
        }

        if (!loan.status().isSubmittedAndPendingApproval()) {
            throw new GeneralPlatformDomainRuleException("error.msg.loan.current.status.is.invalid",
                    "Loan Account current status is invalid. Expected SUBMITTED_AND_PENDING_APPROVAL but found " + loan.status().getCode());
        }

        // Validate current decision state matches expected state for this level
        Integer expectedPreviousState = levelNumber == 1 ?
                LoanDecisionState.DUE_DILIGENCE.getValue() :
                dynamicIcReviewLevelHelper.getPreviousIcReviewDecisionState(levelConfig.getDecisionStateValue());

        if (expectedPreviousState != null && !loan.getLoanDecisionState().equals(expectedPreviousState)) {
            throw new GeneralPlatformDomainRuleException("error.msg.loan.decision.state.is.invalid",
                    "Loan Account Decision state is invalid for IC Review Level " + levelNumber + ". Expected " + expectedPreviousState + " but found " + loan.getLoanDecisionState());
        }

        if (!loan.getLoanDecisionState().equals(loanDecision.getLoanDecisionState())) {
            throw new GeneralPlatformDomainRuleException("error.msg.loan.decision.state.does.not.reconcile",
                    "Loan Account Decision state Does not reconcile. Operation is terminated");
        }
    }

    /**
     * Get the date of the previous stage for validation
     */
    private LocalDate getPreviousStageDate(LoanDecision loanDecision, Integer levelNumber) {
        // Validate levelNumber to prevent arithmetic underflow
        if (levelNumber == null || levelNumber < 1) {
            return null;
        }

        if (levelNumber == 1) {
            return loanDecision.getDueDiligenceOn();
        } else if (levelNumber == 2) {
            return loanDecision.getIcReviewDecisionLevelOneOn();
        } else if (levelNumber == 3) {
            return loanDecision.getIcReviewDecisionLevelTwoOn();
        } else if (levelNumber == 4) {
            return loanDecision.getIcReviewDecisionLevelThreeOn();
        } else if (levelNumber == 5) {
            return loanDecision.getIcReviewDecisionLevelFourOn();
        } else if (levelNumber > 5) {
            // For levels > 5, check the dynamic table
            // Safe: levelNumber is validated to be > 5, so levelNumber - 1 >= 5
            int previousLevelNumber = levelNumber - 1;
            LoanDecisionLevel level = loanDecisionLevelRepository.findByLoanIdAndLevelNumber(
                    loanDecision.getLoan().getId(), previousLevelNumber);
            return level != null ? level.getDecisionOn() : null;
        }
        return null;
    }

    /**
     * Update legacy IC review fields for backward compatibility (levels 1-5)
     */
    private void updateLegacyIcReviewFields(LoanDecision loanDecision, Integer levelNumber, JsonCommand command,
            AppUser currentUser, LocalDate icReviewOn, BigDecimal recommendedAmount,
            Integer termFrequency, Integer termPeriodFrequencyEnum, boolean isReject) {

        String note = command.stringValueOfParameterNamed("note");

        switch (levelNumber) {
            case 1:
                loanDecision.setIcReviewDecisionLevelOneNote(note);
                loanDecision.setIcReviewDecisionLevelOneSigned(!isReject);
                loanDecision.setRejectIcReviewDecisionLevelOneSigned(isReject);
                loanDecision.setIcReviewDecisionLevelOneOn(icReviewOn);
                loanDecision.setIcReviewDecisionLevelOneBy(currentUser);
                loanDecision.setIcReviewDecisionLevelOneRecommendedAmount(recommendedAmount);
                loanDecision.setIcReviewDecisionLevelOneTermFrequency(termFrequency);
                loanDecision.setIcReviewDecisionLevelOneTermPeriodFrequencyEnum(termPeriodFrequencyEnum);
                break;
            case 2:
                loanDecision.setIcReviewDecisionLevelTwoNote(note);
                loanDecision.setIcReviewDecisionLevelTwoSigned(!isReject);
                loanDecision.setRejectIcReviewDecisionLevelTwoSigned(isReject);
                loanDecision.setIcReviewDecisionLevelTwoOn(icReviewOn);
                loanDecision.setIcReviewDecisionLevelTwoBy(currentUser);
                loanDecision.setIcReviewDecisionLevelTwoRecommendedAmount(recommendedAmount);
                loanDecision.setIcReviewDecisionLevelTwoTermFrequency(termFrequency);
                loanDecision.setIcReviewDecisionLevelTwoTermPeriodFrequencyEnum(termPeriodFrequencyEnum);
                break;
            case 3:
                loanDecision.setIcReviewDecisionLevelThreeNote(note);
                loanDecision.setIcReviewDecisionLevelThreeSigned(!isReject);
                loanDecision.setRejectIcReviewDecisionLevelThreeSigned(isReject);
                loanDecision.setIcReviewDecisionLevelThreeOn(icReviewOn);
                loanDecision.setIcReviewDecisionLevelThreeBy(currentUser);
                loanDecision.setIcReviewDecisionLevelThreeRecommendedAmount(recommendedAmount);
                loanDecision.setIcReviewDecisionLevelThreeTermFrequency(termFrequency);
                loanDecision.setIcReviewDecisionLevelThreeTermPeriodFrequencyEnum(termPeriodFrequencyEnum);
                break;
            case 4:
                loanDecision.setIcReviewDecisionLevelFourNote(note);
                loanDecision.setIcReviewDecisionLevelFourSigned(!isReject);
                loanDecision.setRejectIcReviewDecisionLevelFourSigned(isReject);
                loanDecision.setIcReviewDecisionLevelFourOn(icReviewOn);
                loanDecision.setIcReviewDecisionLevelFourBy(currentUser);
                loanDecision.setIcReviewDecisionLevelFourRecommendedAmount(recommendedAmount);
                loanDecision.setIcReviewDecisionLevelFourTermFrequency(termFrequency);
                loanDecision.setIcReviewDecisionLevelFourTermPeriodFrequencyEnum(termPeriodFrequencyEnum);
                break;
            case 5:
                loanDecision.setIcReviewDecisionLevelFiveNote(note);
                loanDecision.setIcReviewDecisionLevelFiveSigned(!isReject);
                loanDecision.setRejectIcReviewDecisionLevelFiveSigned(isReject);
                loanDecision.setIcReviewDecisionLevelFiveOn(icReviewOn);
                loanDecision.setIcReviewDecisionLevelFiveBy(currentUser);
                loanDecision.setIcReviewDecisionLevelFiveRecommendedAmount(recommendedAmount);
                loanDecision.setIcReviewDecisionLevelFiveTermFrequency(termFrequency);
                loanDecision.setIcReviewDecisionLevelFiveTermPeriodFrequencyEnum(termPeriodFrequencyEnum);
                break;
        }
    }

    /**
     * Update legacy IC review reject fields for backward compatibility (levels 1-5)
     */
    private void updateLegacyIcReviewRejectFields(LoanDecision loanDecision, Integer levelNumber) {
        switch (levelNumber) {
            case 1:
                loanDecision.setRejectIcReviewDecisionLevelOneSigned(true);
                break;
            case 2:
                loanDecision.setRejectIcReviewDecisionLevelTwoSigned(true);
                break;
            case 3:
                loanDecision.setRejectIcReviewDecisionLevelThreeSigned(true);
                break;
            case 4:
                loanDecision.setRejectIcReviewDecisionLevelFourSigned(true);
                break;
            case 5:
                loanDecision.setRejectIcReviewDecisionLevelFiveSigned(true);
                break;
        }
    }

    /**
     * Set next approver dynamically - supports both legacy and new levels
     */
    private void setNextApproverDynamic(LoanDecision decision, Integer nextStage, AppUser nextApprover) {
        // First try the legacy switch statement
        setNextApprover(decision, nextStage, nextApprover);

        // For levels beyond 5, store in the dynamic table
        if (dynamicIcReviewLevelHelper.isIcReviewLevel(nextStage)) {
            Integer levelNumber = dynamicIcReviewLevelHelper.getIcReviewLevelNumber(nextStage);
            if (levelNumber != null && levelNumber > 5) {
                // The next approver will be set when the level is actually processed
                // For now, we just ensure the next stage is set correctly
                decision.setNextLoanIcReviewDecisionState(nextStage);
            }
        }
    }
}
