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
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.portfolio.client.data.PartnerClientVerificationRequest;
import org.apache.fineract.portfolio.client.data.PartnerClientVerificationResponse;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.client.domain.ClientOtherInfo;
import org.apache.fineract.portfolio.client.domain.ClientOtherInfoRepository;
import org.apache.fineract.portfolio.client.domain.ClientRepositoryWrapper;
import org.apache.fineract.portfolio.client.domain.PartnerClientVerificationAudit;
import org.apache.fineract.portfolio.client.domain.PartnerClientVerificationAuditRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PartnerClientVerificationServiceImpl implements PartnerClientVerificationService {

    private static final Logger logger = LoggerFactory.getLogger(PartnerClientVerificationServiceImpl.class);
    private static final int WRITTEN_OFF_STATUS = 601;

    private final ClientRepositoryWrapper clientRepositoryWrapper;
    private final ClientOtherInfoRepository clientOtherInfoRepository;
    private final PartnerClientVerificationAuditRepository auditRepository;
    private final JdbcTemplate jdbcTemplate;
    private final PartnerClientVerificationRateLimiter rateLimiter;

    @Autowired
    public PartnerClientVerificationServiceImpl(final ClientRepositoryWrapper clientRepositoryWrapper,
            final ClientOtherInfoRepository clientOtherInfoRepository, final PartnerClientVerificationAuditRepository auditRepository,
            final JdbcTemplate jdbcTemplate, final PartnerClientVerificationRateLimiter rateLimiter) {
        this.clientRepositoryWrapper = clientRepositoryWrapper;
        this.clientOtherInfoRepository = clientOtherInfoRepository;
        this.auditRepository = auditRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.rateLimiter = rateLimiter;
    }

    @Override
    @Transactional
    public PartnerClientVerificationResponse verifyClient(final PartnerClientVerificationRequest request) {
        this.rateLimiter.check(request.getNationalId(), request.getPhoneNumber());

        logger.info("Partner client verification request from {}: nationalIdHash={}, phoneHash={}", request.getSourceSystem(),
                PartnerClientVerificationRateLimiter.hashIdentifier(request.getNationalId()),
                PartnerClientVerificationRateLimiter.hashIdentifier(request.getPhoneNumber()));

        Client client;
        try {
            client = findClientByNationalIdOrPhoneNumber(request.getNationalId(), request.getPhoneNumber());
        } catch (final DataAccessException ex) {
            logger.error("Database failure during partner client verification: {}", ex.getMessage());
            throw ex;
        }

        if (client == null) {
            logger.info("Client not found for provided identifiers");
            logVerificationAttempt(request, null, false, "NOT_FOUND", "NOT_ELIGIBLE", "Client not found in CBS");
            return PartnerClientVerificationResponse.notRegistered();
        }

        final boolean isEligible = checkClientEligibility(client);
        final String remarks = isEligible ? "Client active and eligible for financing"
                : "Client not eligible for financing (status: " + client.getStatus() + ")";

        final PartnerClientVerificationResponse response = isEligible
                ? PartnerClientVerificationResponse.verifiedEligible(client.getId().toString(), remarks)
                : PartnerClientVerificationResponse.verifiedIneligible(client.getId().toString(), remarks);

        logVerificationAttempt(request, client, true, "VERIFIED", isEligible ? "ELIGIBLE" : "NOT_ELIGIBLE", remarks);

        logger.info("Verification completed for client {}: isRegistered={}, eligibilityStatus={}", client.getId(), response.isRegistered(),
                response.getEligibilityStatus());

        return response;
    }

    private Client findClientByNationalIdOrPhoneNumber(final String nationalId, final String phoneNumber) {
        if (StringUtils.isNotBlank(nationalId)) {
            final List<ClientOtherInfo> clientOtherInfos = this.clientOtherInfoRepository
                    .findByNationalIdentificationNumber(nationalId.trim());
            if (!clientOtherInfos.isEmpty() && clientOtherInfos.get(0).getClient() != null) {
                return this.clientRepositoryWrapper.findOneWithNotFoundDetection(clientOtherInfos.get(0).getClient().getId());
            }
        }

        if (StringUtils.isNotBlank(phoneNumber)) {
            return this.clientRepositoryWrapper.findByMobileNo(phoneNumber.trim());
        }

        return null;
    }

    private boolean checkClientEligibility(final Client client) {
        if (!client.isActive()) {
            return false;
        }
        return !hasActiveWriteOff(client.getId());
    }

    private boolean hasActiveWriteOff(final Long clientId) {
        try {
            final Integer count = this.jdbcTemplate.queryForObject(
                    "select count(*) from m_loan where client_id = ? and loan_status_id = ?", Integer.class, clientId, WRITTEN_OFF_STATUS);
            return count != null && count > 0;
        } catch (final DataAccessException ex) {
            logger.error("Failed to check write-offs for client {}: {}", clientId, ex.getMessage());
            throw ex;
        }
    }

    private void logVerificationAttempt(final PartnerClientVerificationRequest request, final Client client, final boolean isRegistered,
            final String verificationStatus, final String eligibilityStatus, final String remarks) {
        try {
            final String clientIdentifier = client != null ? client.getId().toString() : "NOT_FOUND";
            String tenantId = "default";
            if (ThreadLocalContextUtil.getTenant() != null) {
                tenantId = ThreadLocalContextUtil.getTenant().getTenantIdentifier();
            }

            final PartnerClientVerificationAudit audit = PartnerClientVerificationAudit.create(maskNationalId(request.getNationalId()),
                    maskPhoneNumber(request.getPhoneNumber()), request.getFullName(), request.getSourceSystem(), clientIdentifier,
                    isRegistered, verificationStatus, eligibilityStatus, remarks, tenantId);

            this.auditRepository.save(audit);
        } catch (final Exception e) {
            logger.error("Error logging verification attempt: {}", e.getMessage());
        }
    }

    private String maskNationalId(final String nationalId) {
        if (nationalId == null || nationalId.length() < 8) {
            return "XXXX";
        }
        return nationalId.substring(0, 4) + "XXXXXXXX" + nationalId.substring(nationalId.length() - 4);
    }

    private String maskPhoneNumber(final String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 4) {
            return "XXXX";
        }
        return phoneNumber.substring(0, phoneNumber.length() - 4) + "XXXX";
    }
}
