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
package org.apache.fineract.organisation.provisioning.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import javax.persistence.PersistenceException;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.fineract.accounting.provisioning.service.ProvisioningEntriesReadPlatformService;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.exception.PlatformDataIntegrityException;
import org.apache.fineract.organisation.provisioning.constants.ProvisioningCriteriaConstants;
import org.apache.fineract.organisation.provisioning.domain.ProvisioningCriteria;
import org.apache.fineract.organisation.provisioning.domain.ProvisioningCriteriaRepository;
import org.apache.fineract.organisation.provisioning.domain.ProvisioningCriteriaVersion;
import org.apache.fineract.organisation.provisioning.exception.ProvisioningCategoryNotFoundException;
import org.apache.fineract.organisation.provisioning.exception.ProvisioningCriteriaCannotBeDeletedException;
import org.apache.fineract.organisation.provisioning.exception.ProvisioningCriteriaNotFoundException;
import org.apache.fineract.organisation.provisioning.serialization.ProvisioningCriteriaDefinitionJsonDeserializer;
import org.apache.fineract.portfolio.loanproduct.domain.LoanProduct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;

@Service
public class ProvisioningCriteriaWritePlatformServiceJpaRepositoryImpl implements ProvisioningCriteriaWritePlatformService {

    private static final Logger LOG = LoggerFactory.getLogger(ProvisioningCriteriaWritePlatformServiceJpaRepositoryImpl.class);

    private final ProvisioningCriteriaDefinitionJsonDeserializer fromApiJsonDeserializer;
    private final ProvisioningCriteriaAssembler provisioningCriteriaAssembler;
    private final ProvisioningCriteriaRepository provisioningCriteriaRepository;
    private final ProvisioningEntriesReadPlatformService provisioningEntriesReadPlatformService;

    @Autowired
    public ProvisioningCriteriaWritePlatformServiceJpaRepositoryImpl(
            final ProvisioningCriteriaDefinitionJsonDeserializer fromApiJsonDeserializer,
            final ProvisioningCriteriaAssembler provisioningCriteriaAssembler,
            final ProvisioningCriteriaRepository provisioningCriteriaRepository,
            final ProvisioningEntriesReadPlatformService provisioningEntriesReadPlatformService) {
        this.fromApiJsonDeserializer = fromApiJsonDeserializer;
        this.provisioningCriteriaAssembler = provisioningCriteriaAssembler;
        this.provisioningCriteriaRepository = provisioningCriteriaRepository;
        this.provisioningEntriesReadPlatformService = provisioningEntriesReadPlatformService;
    }

    @Override
    public CommandProcessingResult createProvisioningCriteria(JsonCommand command) {
        try {
            this.fromApiJsonDeserializer.validateForCreate(command.json());
            ProvisioningCriteria provisioningCriteria = provisioningCriteriaAssembler.fromParsedJson(command.parsedJson());
            this.provisioningCriteriaRepository.saveAndFlush(provisioningCriteria);
            return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(provisioningCriteria.getId())
                    .build();
        } catch (final JpaSystemException | DataIntegrityViolationException dve) {
            handleDataIntegrityIssues(command, dve.getMostSpecificCause(), dve);
            return CommandProcessingResult.empty();
        } catch (final PersistenceException dve) {
            Throwable throwable = ExceptionUtils.getRootCause(dve.getCause());
            handleDataIntegrityIssues(command, throwable, dve);
            return CommandProcessingResult.empty();
        }
    }

    @Override
    public CommandProcessingResult deleteProvisioningCriteria(Long criteriaId) {
        this.provisioningCriteriaRepository.findById(criteriaId).orElseThrow(() -> new ProvisioningCriteriaNotFoundException(criteriaId));
        if (this.provisioningEntriesReadPlatformService.retrieveProvisioningEntryDataByCriteriaId(criteriaId) != null) {
            throw new ProvisioningCriteriaCannotBeDeletedException(criteriaId);
        }
        this.provisioningCriteriaRepository.deleteById(criteriaId);
        return new CommandProcessingResultBuilder().withEntityId(criteriaId).build();
    }

    @Override
    public CommandProcessingResult updateProvisioningCriteria(final Long criteriaId, JsonCommand command) {
        try {
            this.fromApiJsonDeserializer.validateForUpdate(command.json());
            ProvisioningCriteria provisioningCriteria = provisioningCriteriaRepository.findById(criteriaId).orElse(null);
            if (provisioningCriteria == null) {
                throw new ProvisioningCategoryNotFoundException(criteriaId);
            }
            List<LoanProduct> products = this.provisioningCriteriaAssembler.parseLoanProducts(command.parsedJson());
            final Map<String, Object> changes = provisioningCriteria.update(command, products);
            boolean definitionsPresent = command.parsedJson().getAsJsonObject()
                    .has(ProvisioningCriteriaConstants.JSON_PROVISIONING_DEFINITIONS_PARAM);
            if (definitionsPresent) {
                createNextCriteriaVersion(provisioningCriteria, command);
            }
            if (!changes.isEmpty() || definitionsPresent) {
                provisioningCriteriaRepository.saveAndFlush(provisioningCriteria);
            }
            return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(provisioningCriteria.getId())
                    .build();
        } catch (final JpaSystemException | DataIntegrityViolationException dve) {
            handleDataIntegrityIssues(command, dve.getMostSpecificCause(), dve);
            return CommandProcessingResult.empty();
        } catch (final PersistenceException dve) {
            Throwable throwable = ExceptionUtils.getRootCause(dve.getCause());
            handleDataIntegrityIssues(command, throwable, dve);
            return CommandProcessingResult.empty();
        }
    }

    private void createNextCriteriaVersion(ProvisioningCriteria provisioningCriteria, JsonCommand command) {
        ProvisioningCriteriaVersion latestVersion = provisioningCriteria.getLatestVersion();
        if (latestVersion == null) {
            throw new PlatformDataIntegrityException("error.msg.provisioningcriteria.missing.version",
                    "Provisioning criteria version history is incomplete");
        }
        LocalDate effectiveFrom = this.provisioningCriteriaAssembler.parseEffectiveFrom(command.parsedJson());
        if (!effectiveFrom.isAfter(latestVersion.getEffectiveFrom())) {
            throw new PlatformDataIntegrityException("error.msg.provisioningcriteria.invalid.effective.from",
                    "The new provisioning configuration must take effect after the current version");
        }
        ProvisioningCriteriaVersion nextVersion = this.provisioningCriteriaAssembler.createProvisioningCriteriaVersion(provisioningCriteria,
                latestVersion.getVersionNo() + 1, effectiveFrom, command.parsedJson());
        latestVersion.retireOn(effectiveFrom.minusDays(1));
        provisioningCriteria.getProvisioningCriteriaVersions().add(nextVersion);
    }

    /*
     * Guaranteed to throw an exception no matter what the data integrity issue is.
     */
    private void handleDataIntegrityIssues(final JsonCommand command, final Throwable realCause, final Exception dve) {
        if (realCause.getMessage().contains("criteria_name")) {
            final String name = command.stringValueOfParameterNamed(ProvisioningCriteriaConstants.JSON_CRITERIANAME_PARAM);
            throw new PlatformDataIntegrityException("error.msg.provisioning.duplicate.criterianame",
                    "Provisioning Criteria with name `" + name + "` already exists", "category name", name);
        } else if (realCause.getMessage().contains("product_id")) {
            throw new PlatformDataIntegrityException("error.msg.provisioning.product.id(s).already.associated.existing.criteria",
                    "The selected products already associated with another Provisioning Criteria");
        }
        LOG.error("Error occured.", dve);
        throw new PlatformDataIntegrityException("error.msg.provisioning.unknown.data.integrity.issue",
                "Unknown data integrity issue with resource: " + realCause.getMessage());
    }
}
