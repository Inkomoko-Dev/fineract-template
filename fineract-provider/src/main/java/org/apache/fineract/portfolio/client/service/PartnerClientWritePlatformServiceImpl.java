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

import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
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
        // Validate partner code
        if (!disbursementProviderReadPlatformService.isValidPartnerCode(partnerCode)) {
            throw new IllegalArgumentException("Invalid partner code: " + partnerCode);
        }

        // Check if client exists
        final Client client = this.clientRepositoryWrapper.findOneWithNotFoundDetection(clientId);

        // Check if mapping already exists
        if (this.partnerClientMappingRepository.findByClientIdAndPartnerCodeAndIsActiveTrue(clientId, partnerCode).isPresent()) {
            throw new IllegalStateException("Client is already assigned to partner: " + partnerCode);
        }

        // Create new mapping
        final AppUser assignedBy = this.context.authenticatedUser();
        final PartnerClientMapping mapping = PartnerClientMapping.create(client, partnerCode, assignedBy);
        this.partnerClientMappingRepository.save(mapping);

        // Create history entry
        final PartnerClientMappingHistory history = PartnerClientMappingHistory.createAssignment(mapping, assignedBy, reason);
        this.partnerClientMappingHistoryRepository.save(history);

        log.info("Assigned client {} to partner {} by user {}", clientId, partnerCode, assignedBy.getId());

        return new CommandProcessingResultBuilder().withEntityId(mapping.getId()).withClientId(clientId)
                .withCommandId(assignedBy.getId()).build();
    }

    @Override
    public CommandProcessingResult reassignClient(final Long clientId, final String partnerCode, final String reason) {
        // Validate new partner code
        if (!disbursementProviderReadPlatformService.isValidPartnerCode(partnerCode)) {
            throw new IllegalArgumentException("Invalid partner code: " + partnerCode);
        }

        // Check if client exists
        final Client client = this.clientRepositoryWrapper.findOneWithNotFoundDetection(clientId);

        // Get existing active mapping
        final PartnerClientMapping existingMapping = this.partnerClientMappingRepository.findByClientId(clientId)
                .orElseThrow(() -> new IllegalStateException("No active partner mapping found for client: " + clientId));

        if (!existingMapping.isActive()) {
            throw new IllegalStateException("Client has no active partner mapping: " + clientId);
        }

        final String previousPartnerCode = existingMapping.getPartnerCode();

        // Check if reassigning to same partner
        if (previousPartnerCode.equals(partnerCode)) {
            throw new IllegalStateException("Client is already assigned to partner: " + partnerCode);
        }

        // Deactivate old mapping
        existingMapping.deactivate();
        this.partnerClientMappingRepository.save(existingMapping);

        // Create new mapping
        final AppUser assignedBy = this.context.authenticatedUser();
        final PartnerClientMapping newMapping = PartnerClientMapping.create(client, partnerCode, assignedBy);
        this.partnerClientMappingRepository.save(newMapping);

        // Create history entry for reassignment
        final PartnerClientMappingHistory history = PartnerClientMappingHistory.createReassignment(newMapping,
                previousPartnerCode, partnerCode, assignedBy, reason);
        this.partnerClientMappingHistoryRepository.save(history);

        log.info("Reassigned client {} from {} to {} by user {}", clientId, previousPartnerCode, partnerCode, assignedBy.getId());

        return new CommandProcessingResultBuilder().withEntityId(newMapping.getId()).withClientId(clientId)
                .withCommandId(assignedBy.getId()).build();
    }

    @Override
    public CommandProcessingResult deactivateClientMapping(final Long clientId, final String reason) {
        // Check if client exists
        final Client client = this.clientRepositoryWrapper.findOneWithNotFoundDetection(clientId);

        // Get existing active mapping
        final PartnerClientMapping existingMapping = this.partnerClientMappingRepository.findByClientId(clientId)
                .orElseThrow(() -> new IllegalStateException("No active partner mapping found for client: " + clientId));

        if (!existingMapping.isActive()) {
            throw new IllegalStateException("Client has no active partner mapping: " + clientId);
        }

        // Deactivate mapping
        existingMapping.deactivate();
        this.partnerClientMappingRepository.save(existingMapping);

        // Create history entry for deactivation
        final AppUser changedBy = this.context.authenticatedUser();
        final PartnerClientMappingHistory history = PartnerClientMappingHistory.createDeactivation(existingMapping, changedBy,
                reason);
        this.partnerClientMappingHistoryRepository.save(history);

        log.info("Deactivated partner mapping for client {} by user {}", clientId, changedBy.getId());

        return new CommandProcessingResultBuilder().withEntityId(existingMapping.getId()).withClientId(clientId)
                .withCommandId(changedBy.getId()).build();
    }
}
