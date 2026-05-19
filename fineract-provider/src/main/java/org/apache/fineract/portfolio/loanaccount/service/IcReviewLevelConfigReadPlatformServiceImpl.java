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
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.exception.PlatformDataIntegrityException;
import org.apache.fineract.portfolio.loanaccount.data.IcReviewLevelConfigData;
import org.apache.fineract.portfolio.loanaccount.domain.IcReviewLevelConfig;
import org.apache.fineract.portfolio.loanaccount.domain.IcReviewLevelConfigRepository;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class IcReviewLevelConfigReadPlatformServiceImpl implements IcReviewLevelConfigReadPlatformService {

    private final IcReviewLevelConfigRepository icReviewLevelConfigRepository;

    @Override
    public List<IcReviewLevelConfigData> retrieveAllActiveLevels() {
        List<IcReviewLevelConfig> levels = this.icReviewLevelConfigRepository.findAllActiveOrderByDisplayOrder();
        return levels.stream().map(this::mapToData).collect(Collectors.toList());
    }

    @Override
    public List<IcReviewLevelConfigData> retrieveAllLevels() {
        List<IcReviewLevelConfig> levels = this.icReviewLevelConfigRepository.findAll();
        return levels.stream().map(this::mapToData).collect(Collectors.toList());
    }

    @Override
    public IcReviewLevelConfigData retrieveLevel(Long levelId) {
        IcReviewLevelConfig level = this.icReviewLevelConfigRepository.findById(levelId)
                .orElseThrow(() -> new PlatformDataIntegrityException(
                        "error.msg.ic.review.level.not.found",
                        "IC Review Level with id " + levelId + " not found"));
        return mapToData(level);
    }

    @Override
    public IcReviewLevelConfigData retrieveLevelByNumber(Integer levelNumber) {
        IcReviewLevelConfig level = this.icReviewLevelConfigRepository.findByLevelNumberAndActive(levelNumber);
        if (level == null) {
            throw new PlatformDataIntegrityException(
                    "error.msg.ic.review.level.not.found.by.number",
                    "IC Review Level with number " + levelNumber + " not found");
        }
        return mapToData(level);
    }

    @Override
    public IcReviewLevelConfigData retrieveLevelByCode(String levelCode) {
        IcReviewLevelConfig level = this.icReviewLevelConfigRepository.findByLevelCodeAndActive(levelCode);
        if (level == null) {
            throw new PlatformDataIntegrityException(
                    "error.msg.ic.review.level.not.found.by.code",
                    "IC Review Level with code " + levelCode + " not found");
        }
        return mapToData(level);
    }

    @Override
    public IcReviewLevelConfigData retrieveLevelByDecisionStateValue(Integer decisionStateValue) {
        IcReviewLevelConfig level = this.icReviewLevelConfigRepository.findByDecisionStateValue(decisionStateValue);
        if (level == null) {
            throw new PlatformDataIntegrityException(
                    "error.msg.ic.review.level.not.found.by.state",
                    "IC Review Level with decision state value " + decisionStateValue + " not found");
        }
        return mapToData(level);
    }

    @Override
    public Long countActiveLevels() {
        return this.icReviewLevelConfigRepository.countActiveLevels();
    }

    @Override
    public IcReviewLevelConfigData retrieveNextLevel(Integer currentLevelNumber) {
        List<IcReviewLevelConfig> levels = this.icReviewLevelConfigRepository.findAllActiveOrderByDisplayOrder();

        for (int i = 0; i < levels.size(); i++) {
            if (levels.get(i).getLevelNumber().equals(currentLevelNumber)) {
                if (i + 1 < levels.size()) {
                    return mapToData(levels.get(i + 1));
                }
                break;
            }
        }
        return null; // No next level
    }

    @Override
    public IcReviewLevelConfigData retrievePreviousLevel(Integer currentLevelNumber) {
        List<IcReviewLevelConfig> levels = this.icReviewLevelConfigRepository.findAllActiveOrderByDisplayOrder();

        for (int i = 0; i < levels.size(); i++) {
            if (levels.get(i).getLevelNumber().equals(currentLevelNumber)) {
                if (i > 0) {
                    return mapToData(levels.get(i - 1));
                }
                break;
            }
        }
        return null; // No previous level
    }

    private IcReviewLevelConfigData mapToData(IcReviewLevelConfig level) {
        return IcReviewLevelConfigData.instance(
                level.getId(),
                level.getLevelNumber(),
                level.getLevelName(),
                level.getLevelCode(),
                level.getDecisionStateValue(),
                level.getIsActive(),
                level.getDisplayOrder()
        );
    }
}
