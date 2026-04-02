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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.organisation.monetary.data.CurrencyData;
import org.apache.fineract.organisation.monetary.service.CurrencyReadPlatformServiceImpl;
import org.apache.fineract.portfolio.loanaccount.data.LoanApprovalMatrixData;
import org.apache.fineract.portfolio.loanaccount.data.LoanApprovalMatrixLevelData;
import org.apache.fineract.portfolio.loanaccount.domain.IcReviewLevelConfig;
import org.apache.fineract.portfolio.loanaccount.domain.IcReviewLevelConfigRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanApprovalMatrix;
import org.apache.fineract.portfolio.loanaccount.domain.LoanApprovalMatrixLevel;
import org.apache.fineract.portfolio.loanaccount.domain.LoanApprovalMatrixLevelRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanApprovalMatrixRepository;
import org.apache.fineract.portfolio.loanaccount.mapper.LoanApprovalMatrixMapper;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoanApprovalMatrixReadPlatformServiceImpl implements LoanApprovalMatrixReadPlatformService {

    private final LoanApprovalMatrixRepository loanApprovalMatrixRepository;
    private final LoanApprovalMatrixLevelRepository loanApprovalMatrixLevelRepository;
    private final IcReviewLevelConfigRepository icReviewLevelConfigRepository;
    private final LoanApprovalMatrixMapper mapper;
    private final CurrencyReadPlatformServiceImpl currencyReadPlatformService;

    @Override
    public List<LoanApprovalMatrixData> findAll() {
        List<LoanApprovalMatrix> loanApprovalMatrices = loanApprovalMatrixRepository.findAll();
        List<LoanApprovalMatrixData> result = mapper.map(loanApprovalMatrices);

        // Enrich with dynamic levels and currency data for each matrix
        for (int i = 0; i < result.size(); i++) {
            LoanApprovalMatrixData matrixData = result.get(i);
            LoanApprovalMatrix matrix = loanApprovalMatrices.get(i);

            // Set currency data
            if (matrix.getCurrency() != null) {
                CurrencyData currencyData = currencyReadPlatformService.retrieveCurrency(matrix.getCurrency());
                matrixData.setCurrencyData(currencyData);
            }

            enrichWithDynamicLevels(matrixData);
        }

        return result;
    }

    @Override
    public LoanApprovalMatrixData getApprovalMatrixDetails(Long approvalMatrixId) {
        LoanApprovalMatrixData loanApprovalMatrixData = null;
        Optional<LoanApprovalMatrix> loanApproval = loanApprovalMatrixRepository.findById(approvalMatrixId);
        if (loanApproval.isPresent()) {
            CurrencyData currencyData = currencyReadPlatformService.retrieveCurrency(loanApproval.get().getCurrency());

            loanApprovalMatrixData = mapper.map(loanApproval.get());
            loanApprovalMatrixData.setCurrencyData(currencyData);

            // Enrich with dynamic levels
            enrichWithDynamicLevels(loanApprovalMatrixData);
        }
        return loanApprovalMatrixData;
    }

    /**
     * Enriches the approval matrix data with dynamic IC review levels.
     * This method merges data from two sources:
     * 1. Active IC review levels from m_ic_review_level_config (shows all available levels)
     * 2. Configured matrix levels from m_loan_approval_matrix_level (shows configured values)
     *
     * This ensures that newly created IC review levels appear in the API response even before
     * they are configured in the approval matrix.
     */
    private void enrichWithDynamicLevels(LoanApprovalMatrixData matrixData) {
        if (matrixData == null || matrixData.getId() == null) {
            return;
        }

        // Get all active IC review level configurations
        List<IcReviewLevelConfig> activeIcLevels = icReviewLevelConfigRepository.findAllActiveOrderByDisplayOrder();

        // Get existing matrix level configurations for this approval matrix
        List<LoanApprovalMatrixLevel> matrixLevels = loanApprovalMatrixLevelRepository
                .findByApprovalMatrixIdOrderByLevelNumber(matrixData.getId());

        // Create a map for quick lookup of matrix levels by level number
        Map<Integer, LoanApprovalMatrixLevel> matrixLevelMap = new HashMap<>();
        if (matrixLevels != null) {
            for (LoanApprovalMatrixLevel level : matrixLevels) {
                matrixLevelMap.put(level.getLevelNumber(), level);
            }
        }

        // Build the result list with all active IC levels
        List<LoanApprovalMatrixLevelData> levelDataList = new ArrayList<>();

        for (IcReviewLevelConfig icLevel : activeIcLevels) {
            Integer levelNumber = icLevel.getLevelNumber();
            LoanApprovalMatrixLevel matrixLevel = matrixLevelMap.get(levelNumber);

            LoanApprovalMatrixLevelData levelData = new LoanApprovalMatrixLevelData();

            // Always set IC review level info from the config
            levelData.setLevelNumber(levelNumber);
            levelData.setIcReviewLevelId(icLevel.getId());
            levelData.setIcReviewLevelName(icLevel.getLevelName());

            // If matrix level exists, populate values; otherwise, leave as null
            if (matrixLevel != null) {
                levelData.setId(matrixLevel.getId());

                // Unsecured First Cycle
                levelData.setUnsecuredFirstCycleMaxAmount(matrixLevel.getUnsecuredFirstCycleMaxAmount());
                levelData.setUnsecuredFirstCycleMinTerm(matrixLevel.getUnsecuredFirstCycleMinTerm());
                levelData.setUnsecuredFirstCycleMaxTerm(matrixLevel.getUnsecuredFirstCycleMaxTerm());

                // Unsecured Second Cycle
                levelData.setUnsecuredSecondCycleMaxAmount(matrixLevel.getUnsecuredSecondCycleMaxAmount());
                levelData.setUnsecuredSecondCycleMinTerm(matrixLevel.getUnsecuredSecondCycleMinTerm());
                levelData.setUnsecuredSecondCycleMaxTerm(matrixLevel.getUnsecuredSecondCycleMaxTerm());

                // Secured First Cycle
                levelData.setSecuredFirstCycleMaxAmount(matrixLevel.getSecuredFirstCycleMaxAmount());
                levelData.setSecuredFirstCycleMinTerm(matrixLevel.getSecuredFirstCycleMinTerm());
                levelData.setSecuredFirstCycleMaxTerm(matrixLevel.getSecuredFirstCycleMaxTerm());

                // Secured Second Cycle
                levelData.setSecuredSecondCycleMaxAmount(matrixLevel.getSecuredSecondCycleMaxAmount());
                levelData.setSecuredSecondCycleMinTerm(matrixLevel.getSecuredSecondCycleMinTerm());
                levelData.setSecuredSecondCycleMaxTerm(matrixLevel.getSecuredSecondCycleMaxTerm());
            }
            // If matrixLevel is null, all amount/term fields remain null (not yet configured)

            levelDataList.add(levelData);
        }

        matrixData.setDynamicLevels(levelDataList);
    }

}
