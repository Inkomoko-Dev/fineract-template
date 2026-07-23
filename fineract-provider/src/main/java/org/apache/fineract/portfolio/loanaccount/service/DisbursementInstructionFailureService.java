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

import org.apache.fineract.portfolio.loanaccount.domain.LoanDisbursementInstructionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists instruction FAILED status in a new transaction so it survives rollback of the outer write.
 */
@Service
public class DisbursementInstructionFailureService {

    private static final Logger LOG = LoggerFactory.getLogger(DisbursementInstructionFailureService.class);

    private final LoanDisbursementInstructionRepository instructionRepository;

    public DisbursementInstructionFailureService(final LoanDisbursementInstructionRepository instructionRepository) {
        this.instructionRepository = instructionRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(final Long instructionId, final String error) {
        if (instructionId == null) {
            return;
        }
        this.instructionRepository.findById(instructionId).ifPresentOrElse(instruction -> {
            instruction.markFailed(error);
            this.instructionRepository.saveAndFlush(instruction);
            LOG.warn("Marked disbursement instruction {} status=FAILED: {}", instructionId, error);
        }, () -> LOG.warn("Could not mark instruction {} FAILED; row not found", instructionId));
    }
}
