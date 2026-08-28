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

import org.apache.fineract.portfolio.client.data.PartnerClientVerificationRequest;
import org.apache.fineract.portfolio.client.data.PartnerClientVerificationResponse;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.client.domain.ClientOtherInfo;
import org.apache.fineract.portfolio.client.domain.ClientOtherInfoRepository;
import org.apache.fineract.portfolio.client.domain.ClientRepository;
import org.apache.fineract.portfolio.client.domain.ClientRepositoryWrapper;
import org.apache.fineract.portfolio.client.domain.PartnerClientVerificationAudit;
import org.apache.fineract.portfolio.client.domain.PartnerClientVerificationAuditRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PartnerClientVerificationServiceImpl implements PartnerClientVerificationService {

    private static final Logger logger = LoggerFactory.getLogger(PartnerClientVerificationServiceImpl.class);

    private final ClientRepository clientRepository;
    private final ClientRepositoryWrapper clientRepositoryWrapper;
    private final ClientOtherInfoRepository clientOtherInfoRepository;
    private final PartnerClientVerificationAuditRepository auditRepository;
    private final PlatformSecurityContext context;

    @Autowired
    public PartnerClientVerificationServiceImpl(final ClientRepository clientRepository,
            final ClientRepositoryWrapper clientRepositoryWrapper,
            final ClientOtherInfoRepository clientOtherInfoRepository,
            final PartnerClientVerificationAuditRepository auditRepository,
            final PlatformSecurityContext context) {
        this.clientRepository = clientRepository;
        this.clientRepositoryWrapper = clientRepositoryWrapper;
        this.clientOtherInfoRepository = clientOtherInfoRepository;
        this.auditRepository = auditRepository;
        this.context = context;
    }

    @Override
    @Transactional
    public PartnerClientVerificationResponse verifyClient(final PartnerClientVerificationRequest request) {
        logger.info("Partner client verification request from {}: nationalId={}, phoneNumber={}", 
                request.getSourceSystem(), maskNationalId(request.getNationalId()), maskPhoneNumber(request.getPhoneNumber()));

        Client client = findClientByNationalIdOrPhoneNumber(request.getNationalId(), request.getPhoneNumber());

        if (client == null) {
            logger.info("Client not found for provided identifiers");
            logVerificationAttempt(request, null, false, "NOT_FOUND", "NOT_ELIGIBLE", "Client not found in CBS");
            return PartnerClientVerificationResponse.notRegistered();
        }

        boolean isEligible = checkClientEligibility(client);
        String remarks = isEligible ? "Client active and eligible for financing" : 
                "Client not eligible for financing (status: " + client.getStatus() + ")";

        PartnerClientVerificationResponse response = isEligible ? 
                PartnerClientVerificationResponse.verifiedEligible(client.getId().toString(), remarks) :
                PartnerClientVerificationResponse.verifiedIneligible(client.getId().toString(), remarks);

        logVerificationAttempt(request, client, true, "VERIFIED", isEligible ? "ELIGIBLE" : "NOT_ELIGIBLE", remarks);

        logger.info("Verification completed for client {}: isRegistered={}, eligibilityStatus={}", 
                client.getId(), response.isRegistered(), response.getEligibilityStatus());

        return response;
    }

    private Client findClientByNationalIdOrPhoneNumber(final String nationalId, final String phoneNumber) {
        // First try to find by national ID
        if (nationalId != null && !nationalId.trim().isEmpty()) {
            List<ClientOtherInfo> clientOtherInfos = clientOtherInfoRepository.findByNationalIdentificationNumber(nationalId.trim());
            if (!clientOtherInfos.isEmpty()) {
                try {
                    return clientRepositoryWrapper.findOneWithNotFoundDetection(clientOtherInfos.get(0).getClient().getId());
                } catch (Exception e) {
                    logger.warn("Error finding client by national ID: {}", e.getMessage());
                }
            }
        }

        // Then try to find by phone number
        if (phoneNumber != null && !phoneNumber.trim().isEmpty()) {
            try {
                return clientRepositoryWrapper.findByMobileNo(phoneNumber.trim());
            } catch (Exception e) {
                logger.warn("Error finding client by phone number: {}", e.getMessage());
            }
        }

        return null;
    }

    private boolean checkClientEligibility(final Client client) {
        // Check if client is active
        if (!client.isActive()) {
            return false;
        }

        // Check for write-offs in the last 12 months
        // This is a simplified check - in production, you would query loan accounts
        // for write-offs within the last 12 months
        // For now, we return true for active clients as a basic eligibility check
        return true;
    }

    private void logVerificationAttempt(final PartnerClientVerificationRequest request, final Client client,
            final boolean isRegistered, final String verificationStatus, final String eligibilityStatus, final String remarks) {
        try {
            String clientIdentifier = client != null ? client.getId().toString() : "NOT_FOUND";
            String tenantId = "default"; // Fallback tenant identifier

            PartnerClientVerificationAudit audit = PartnerClientVerificationAudit.create(
                    maskNationalId(request.getNationalId()),
                    maskPhoneNumber(request.getPhoneNumber()),
                    request.getFullName(),
                    request.getSourceSystem(),
                    clientIdentifier,
                    isRegistered,
                    verificationStatus,
                    eligibilityStatus,
                    remarks,
                    tenantId
            );

            this.auditRepository.save(audit);

            logger.info("Partner verification audit saved - source: {}, client: {}, isRegistered: {}, verificationStatus: {}, eligibilityStatus: {}",
                    request.getSourceSystem(), clientIdentifier, isRegistered, verificationStatus, eligibilityStatus);
        } catch (Exception e) {
            logger.error("Error logging verification attempt: {}", e.getMessage());
        }
    }

    private String maskNationalId(String nationalId) {
        if (nationalId == null || nationalId.length() < 8) {
            return "XXXX";
        }
        return nationalId.substring(0, 4) + "XXXXXXXX" + nationalId.substring(nationalId.length() - 4);
    }

    private String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 4) {
            return "XXXX";
        }
        return phoneNumber.substring(0, phoneNumber.length() - 4) + "XXXX";
    }
}