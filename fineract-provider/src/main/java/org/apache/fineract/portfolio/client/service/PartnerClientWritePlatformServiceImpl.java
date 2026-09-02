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
package org.apache.fineract.portfolio.client.service;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.client.domain.ClientRepositoryWrapper;
import org.apache.fineract.portfolio.client.domain.PartnerClientMapping;
import org.apache.fineract.portfolio.client.domain.PartnerClientMappingHistory;
import org.apache.fineract.portfolio.client.domain.PartnerClientMappingHistoryRepository;
import org.apache.fineract.portfolio.client.domain.PartnerClientMappingRepository;
import org.apache.fineract.portfolio.loanproduct.service.DisbursementProviderReadPlatformService;
import org.apache.fineract.useradministration.domain.AppUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@Transactional
public class PartnerClientWritePlatformServiceImpl implements PartnerClientWritePlatformService {

    private final PartnerClientMappingRepository partnerClientMappingRepository;
    private final PartnerClientMappingHistoryRepository partnerClientMappingHistoryRepository;
    private final ClientRepositoryWrapper clientRepositoryWrapper;
    private final DisbursementProviderReadPlatformService disbursementProviderReadPlatformService;
    private final PlatformSecurityContext context;

    @Autowired
    public PartnerClientWritePlatformServiceImpl(final PartnerClientMappingRepository partnerClientMappingRepository,
            final PartnerClientMappingHistoryRepository partnerClientMappingHistoryRepository,
            final ClientRepositoryWrapper clientRepositoryWrapper,
            final DisbursementProviderReadPlatformService disbursementProviderReadPlatformService,
            final PlatformSecurityContext context) {
        this.partnerClientMappingRepository = partnerClientMappingRepository;
        this.partnerClientMappingHistoryRepository = partnerClientMappingHistoryRepository;
        this.clientRepositoryWrapper = clientRepositoryWrapper;
        this.disbursementProviderReadPlatformService = disbursementProviderReadPlatformService;
        this.context = context;
    }

    @Override
    public CommandProcessingResult assignClientToPartner(final Long clientId, final String partnerCode, final String reason) {
        validatePartnerCode(partnerCode);

        final Client client = this.clientRepositoryWrapper.findOneWithNotFoundDetection(clientId);

        final PartnerClientMapping activeMapping = this.partnerClientMappingRepository.findByClientIdAndIsActiveTrue(clientId).orElse(null);
        if (activeMapping != null) {
            if (partnerCode.equals(activeMapping.getPartnerCode())) {
                throw validationError("validation.msg.partnerClient.alreadyAssigned",
                        "Client is already assigned to partner: " + partnerCode, "partnerCode", partnerCode);
            }
            return reassignClient(clientId, partnerCode, reason);
        }

        final AppUser assignedBy = this.context.authenticatedUser();
        final PartnerClientMapping mapping = PartnerClientMapping.create(client, partnerCode, assignedBy);
        this.partnerClientMappingRepository.save(mapping);

        final PartnerClientMappingHistory history = PartnerClientMappingHistory.createAssignment(mapping, assignedBy, reason);
        this.partnerClientMappingHistoryRepository.save(history);

        log.info("Assigned client {} to partner {} by user {}", clientId, partnerCode, assignedBy.getId());

        return assignmentResult(mapping.getId(), clientId);
    }

    @Override
    public CommandProcessingResult reassignClient(final Long clientId, final String partnerCode, final String reason) {
        validatePartnerCode(partnerCode);

        final Client client = this.clientRepositoryWrapper.findOneWithNotFoundDetection(clientId);

        final PartnerClientMapping existingMapping = this.partnerClientMappingRepository.findByClientIdAndIsActiveTrue(clientId)
                .orElseThrow(() -> validationError("validation.msg.partnerClient.mapping.notFound",
                        "No active partner mapping found for client: " + clientId, "clientId", clientId));

        final String previousPartnerCode = existingMapping.getPartnerCode();
        if (previousPartnerCode.equals(partnerCode)) {
            throw validationError("validation.msg.partnerClient.alreadyAssigned",
                    "Client is already assigned to partner: " + partnerCode, "partnerCode", partnerCode);
        }

        existingMapping.deactivate();
        this.partnerClientMappingRepository.save(existingMapping);

        final AppUser assignedBy = this.context.authenticatedUser();
        final PartnerClientMapping newMapping = PartnerClientMapping.create(client, partnerCode, assignedBy);
        this.partnerClientMappingRepository.save(newMapping);

        final PartnerClientMappingHistory history = PartnerClientMappingHistory.createReassignment(newMapping, previousPartnerCode,
                partnerCode, assignedBy, reason);
        this.partnerClientMappingHistoryRepository.save(history);

        log.info("Reassigned client {} from {} to {} by user {}", clientId, previousPartnerCode, partnerCode, assignedBy.getId());

        return assignmentResult(newMapping.getId(), clientId);
    }

    @Override
    public CommandProcessingResult deactivateClientMapping(final Long clientId, final String reason) {
        this.clientRepositoryWrapper.findOneWithNotFoundDetection(clientId);

        final PartnerClientMapping existingMapping = this.partnerClientMappingRepository.findByClientIdAndIsActiveTrue(clientId)
                .orElseThrow(() -> validationError("validation.msg.partnerClient.mapping.notFound",
                        "No active partner mapping found for client: " + clientId, "clientId", clientId));

        existingMapping.deactivate();
        this.partnerClientMappingRepository.save(existingMapping);

        final AppUser changedBy = this.context.authenticatedUser();
        final PartnerClientMappingHistory history = PartnerClientMappingHistory.createDeactivation(existingMapping, changedBy, reason);
        this.partnerClientMappingHistoryRepository.save(history);

        log.info("Deactivated partner mapping for client {} by user {}", clientId, changedBy.getId());

        return assignmentResult(existingMapping.getId(), clientId);
    }

    private void validatePartnerCode(final String partnerCode) {
        if (!this.disbursementProviderReadPlatformService.isValidPartnerCode(partnerCode)) {
            throw validationError("validation.msg.partnerClient.partnerCode.invalid", "Invalid partner code: " + partnerCode,
                    "partnerCode", partnerCode);
        }
    }

    private static CommandProcessingResult assignmentResult(final Long mappingId, final Long clientId) {
        return new CommandProcessingResultBuilder().withEntityId(mappingId).withClientId(clientId).build();
    }

    private static PlatformApiDataValidationException validationError(final String code, final String message, final String parameter,
            final Object value) {
        return new PlatformApiDataValidationException(code, message,
                List.of(ApiParameterError.parameterError(code, message, parameter, value)));
    }
}
