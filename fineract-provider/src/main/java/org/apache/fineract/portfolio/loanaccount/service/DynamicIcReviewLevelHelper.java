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

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.portfolio.loanaccount.data.IcReviewLevelConfigData;
import org.apache.fineract.portfolio.loanaccount.domain.IcReviewLevelConfig;
import org.apache.fineract.portfolio.loanaccount.domain.IcReviewLevelConfigRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanDecisionState;
import org.springframework.stereotype.Component;

/**
 * Helper class to work with dynamic IC review levels
 * Provides utility methods to determine next/previous levels and permissions
 */
@Component
@RequiredArgsConstructor
public class DynamicIcReviewLevelHelper {

    private final IcReviewLevelConfigRepository icReviewLevelConfigRepository;
    private final IcReviewLevelConfigReadPlatformService icReviewLevelConfigReadPlatformService;

    // Hardcoded level names for levels 1-5 (used when database lookup fails)
    private static final String[] HARDCODED_LEVEL_NAMES = {
        null, // index 0 unused
        "ONE", "TWO", "THREE", "FOUR", "FIVE"
    };

    /**
     * Get the permission code for accepting a specific IC review level
     * Permission format: ACCEPT_LOANICREVIEWDECISIONLEVEL{NAME}
     */
    public String getAcceptPermissionForLevel(Integer levelNumber) {
        if (levelNumber == null || levelNumber < 1) {
            return null;
        }
        String levelName = getLevelNameForPermission(levelNumber);
        if (levelName == null) {
            return null;
        }
        return "ACCEPT_LOANICREVIEWDECISIONLEVEL" + levelName;
    }

    /**
     * Get the permission code for rejecting a specific IC review level
     * Permission format: REJECT_LOANICREVIEWDECISIONLEVEL{NAME}
     */
    public String getRejectPermissionForLevel(Integer levelNumber) {
        if (levelNumber == null || levelNumber < 1) {
            return null;
        }
        String levelName = getLevelNameForPermission(levelNumber);
        if (levelName == null) {
            return null;
        }
        return "REJECT_LOANICREVIEWDECISIONLEVEL" + levelName;
    }

    /**
     * Get the permission code for reading a specific IC review level
     * Permission format: READ_LOANICREVIEWDECISIONLEVEL{NAME}
     */
    public String getReadPermissionForLevel(Integer levelNumber) {
        if (levelNumber == null || levelNumber < 1) {
            return null;
        }
        String levelName = getLevelNameForPermission(levelNumber);
        if (levelName == null) {
            return null;
        }
        return "READ_LOANICREVIEWDECISIONLEVEL" + levelName;
    }

    /**
     * Get the level name (ONE, TWO, THREE, etc.) for permission generation.
     * First tries database lookup, then falls back to hardcoded names for levels 1-5.
     */
    private String getLevelNameForPermission(Integer levelNumber) {
        // Try database lookup first
        IcReviewLevelConfig level = icReviewLevelConfigRepository.findByLevelNumberAndActive(levelNumber);
        if (level != null && level.getLevelCode() != null) {
            // Extract level name from code like IC_REVIEW_LEVEL_ONE -> ONE
            String code = level.getLevelCode();
            int lastUnderscore = code.lastIndexOf('_');
            if (lastUnderscore >= 0 && lastUnderscore < code.length() - 1) {
                return code.substring(lastUnderscore + 1);
            }
        }

        // Fallback to hardcoded names for levels 1-5
        if (levelNumber >= 1 && levelNumber <= 5) {
            return HARDCODED_LEVEL_NAMES[levelNumber];
        }

        // For dynamic levels beyond 5 without database config, return null
        return null;
    }

    /**
     * Get the next IC review decision state after the current state
     * Returns null if there's no next IC review level (i.e., should move to PREPARE_AND_SIGN_CONTRACT)
     */
    public Integer getNextIcReviewDecisionState(Integer currentDecisionState) {
        IcReviewLevelConfig currentLevel = icReviewLevelConfigRepository.findByDecisionStateValue(currentDecisionState);
        if (currentLevel == null) {
            return null;
        }

        IcReviewLevelConfigData nextLevel = icReviewLevelConfigReadPlatformService.retrieveNextLevel(currentLevel.getLevelNumber());
        return nextLevel != null ? nextLevel.getDecisionStateValue() : null;
    }

    /**
     * Get the previous IC review decision state before the current state
     */
    public Integer getPreviousIcReviewDecisionState(Integer currentDecisionState) {
        IcReviewLevelConfig currentLevel = icReviewLevelConfigRepository.findByDecisionStateValue(currentDecisionState);
        if (currentLevel == null) {
            return null;
        }

        IcReviewLevelConfigData previousLevel = icReviewLevelConfigReadPlatformService.retrievePreviousLevel(currentLevel.getLevelNumber());
        return previousLevel != null ? previousLevel.getDecisionStateValue() : null;
    }

    /**
     * Check if a decision state is an IC review level.
     * First tries database lookup, then falls back to checking hardcoded enum.
     */
    public boolean isIcReviewLevel(Integer decisionState) {
        if (decisionState == null) {
            return false;
        }
        // Try database lookup first
        if (icReviewLevelConfigRepository.findByDecisionStateValue(decisionState) != null) {
            return true;
        }
        // Fallback to hardcoded enum check
        LoanDecisionState state = LoanDecisionState.fromInt(decisionState);
        return state.isAnyIcReviewLevel();
    }

    /**
     * Get the IC review level number from decision state.
     * First tries database lookup, then falls back to hardcoded enum calculation.
     */
    public Integer getIcReviewLevelNumber(Integer decisionState) {
        if (decisionState == null) {
            return null;
        }
        // Try database lookup first
        IcReviewLevelConfig level = icReviewLevelConfigRepository.findByDecisionStateValue(decisionState);
        if (level != null) {
            return level.getLevelNumber();
        }
        // Fallback to hardcoded enum calculation for levels 1-5
        LoanDecisionState state = LoanDecisionState.fromInt(decisionState);
        return state.getIcReviewLevelNumber();
    }

    /**
     * Get the first IC review level decision state
     */
    public Integer getFirstIcReviewDecisionState() {
        List<IcReviewLevelConfig> levels = icReviewLevelConfigRepository.findAllActiveOrderByDisplayOrder();
        return levels.isEmpty() ? null : levels.get(0).getDecisionStateValue();
    }

    /**
     * Get the last IC review level decision state
     */
    public Integer getLastIcReviewDecisionState() {
        List<IcReviewLevelConfig> levels = icReviewLevelConfigRepository.findAllActiveOrderByDisplayOrder();
        return levels.isEmpty() ? null : levels.get(levels.size() - 1).getDecisionStateValue();
    }

    /**
     * Get the maximum IC review level number (the highest level configured)
     * Returns null if no levels are configured
     */
    public Integer getMaxIcReviewLevel() {
        List<IcReviewLevelConfig> levels = icReviewLevelConfigRepository.findAllActiveOrderByDisplayOrder();
        return levels.isEmpty() ? null : levels.get(levels.size() - 1).getLevelNumber();
    }

    /**
     * Determine the next decision state after completing current IC review level
     * If it's the last IC review level, return PREPARE_AND_SIGN_CONTRACT state
     * Otherwise, return the next IC review level state
     */
    public Integer determineNextDecisionStateAfterIcReview(Integer currentIcReviewState) {
        Integer nextIcReviewState = getNextIcReviewDecisionState(currentIcReviewState);
        if (nextIcReviewState != null) {
            return nextIcReviewState;
        }
        // No more IC review levels, move to PREPARE_AND_SIGN_CONTRACT
        return LoanDecisionState.PREPARE_AND_SIGN_CONTRACT.getValue();
    }

    /**
     * Get the permission for the next stage after current loan decision state
     */
    public String getNextStagePermission(Integer currentDecisionState) {
        LoanDecisionState state = LoanDecisionState.fromInt(currentDecisionState);

        switch (state) {
            case REVIEW_APPLICATION:
                // Next is first IC review level
                Integer firstIcLevel = getFirstIcReviewDecisionState();
                if (firstIcLevel != null) {
                    Integer levelNumber = getIcReviewLevelNumber(firstIcLevel);
                    return getAcceptPermissionForLevel(levelNumber);
                }
                return null;

            case DUE_DILIGENCE:
                // Next is first IC review level (after due diligence)
                Integer firstLevel = getFirstIcReviewDecisionState();
                if (firstLevel != null) {
                    Integer levelNumber = getIcReviewLevelNumber(firstLevel);
                    return getAcceptPermissionForLevel(levelNumber);
                }
                return null;

            case COLLATERAL_REVIEW:
                // Next is first IC review level (after collateral review)
                Integer firstLevelAfterCollateral = getFirstIcReviewDecisionState();
                if (firstLevelAfterCollateral != null) {
                    Integer levelNumber = getIcReviewLevelNumber(firstLevelAfterCollateral);
                    return getAcceptPermissionForLevel(levelNumber);
                }
                return null;

            default:
                // Check if current state is an IC review level
                if (isIcReviewLevel(currentDecisionState)) {
                    // Return permission for the CURRENT level (users who can approve this level)
                    Integer levelNumber = getIcReviewLevelNumber(currentDecisionState);
                    return getAcceptPermissionForLevel(levelNumber);
                }
                return null;
        }
    }
}
