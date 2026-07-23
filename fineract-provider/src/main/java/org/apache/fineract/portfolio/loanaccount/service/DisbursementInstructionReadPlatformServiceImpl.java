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
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.loanaccount.data.DisbursementInstructionData;
import org.apache.fineract.portfolio.loanaccount.domain.LoanDisbursementInstruction;
import org.apache.fineract.portfolio.loanaccount.domain.LoanDisbursementInstructionRepository;
import org.apache.fineract.portfolio.loanaccount.exception.LoanDisbursementInstructionNotFoundException;
import org.apache.fineract.portfolio.loanproduct.service.DisbursementPartnerAccessService;
import org.apache.fineract.useradministration.domain.AppUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DisbursementInstructionReadPlatformServiceImpl implements DisbursementInstructionReadPlatformService {

    private final LoanDisbursementInstructionRepository instructionRepository;
    private final PlatformSecurityContext context;
    private final DisbursementPartnerAccessService disbursementPartnerAccessService;

    @Override
    public DisbursementInstructionData retrieveOne(final Long instructionId) {
        final LoanDisbursementInstruction instruction = this.instructionRepository.findById(instructionId)
                .orElseThrow(() -> new LoanDisbursementInstructionNotFoundException(instructionId));
        assertCallerCanAccessProvider(instruction.getDisbursementProviderCode());
        return DisbursementInstructionData.from(instruction);
    }

    @Override
    public List<DisbursementInstructionData> retrieveByLoanId(final Long loanId) {
        final String boundProvider = requireBoundProvider();
        return this.instructionRepository.findByLoanIdOrderByIdDesc(loanId).stream()
                .filter(instruction -> boundProvider.equals(instruction.getDisbursementProviderCode()))
                .map(DisbursementInstructionData::from).collect(Collectors.toList());
    }

    private void assertCallerCanAccessProvider(final String providerCode) {
        final String boundProvider = requireBoundProvider();
        if (!boundProvider.equals(providerCode)) {
            throw new LoanDisbursementInstructionNotFoundException();
        }
    }

    private String requireBoundProvider() {
        final AppUser user = this.context.authenticatedUser();
        final String boundProvider = this.disbursementPartnerAccessService.resolveProviderCodeForUser(user).orElse(null);
        if (StringUtils.isBlank(boundProvider)) {
            throw new PlatformApiDataValidationException("validation.msg.disbursementInstruction.partnerBinding.required",
                    "Authenticated user is not bound to a disbursement provider.",
                    List.of(ApiParameterError.generalError("validation.msg.disbursementInstruction.partnerBinding.required",
                            "Authenticated user is not bound to a disbursement provider.")));
        }
        return boundProvider;
    }
}
