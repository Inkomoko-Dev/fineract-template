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
package org.apache.fineract.organisation.externalmapping.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.organisation.externalmapping.data.ExternalOrganizationMappingData;
import org.apache.fineract.organisation.externalmapping.data.ExternalOrganizationMappingImportResult;
import org.apache.fineract.organisation.externalmapping.domain.ExternalOrganizationMapping;
import org.apache.fineract.organisation.externalmapping.domain.ExternalOrganizationMappingRepository;
import org.apache.fineract.organisation.externalmapping.exception.ExternalOrganizationMappingNotFoundException;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.organisation.office.domain.OfficeRepository;
import org.apache.fineract.organisation.office.exception.OfficeNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExternalOrganizationMappingServiceImpl implements ExternalOrganizationMappingService {

    private final ExternalOrganizationMappingRepository mappingRepository;
    private final OfficeRepository officeRepository;
    private final FromJsonHelper fromJsonHelper;

    public ExternalOrganizationMappingServiceImpl(final ExternalOrganizationMappingRepository mappingRepository,
            final OfficeRepository officeRepository, final FromJsonHelper fromJsonHelper) {
        this.mappingRepository = mappingRepository;
        this.officeRepository = officeRepository;
        this.fromJsonHelper = fromJsonHelper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExternalOrganizationMappingData> retrieveAll() {
        return mappingRepository.findAllByOrderByExternalNameAscOfficeIdAsc().stream().map(this::toData).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public ExternalOrganizationMappingData retrieveOne(final Long mappingId) {
        return toData(mapping(mappingId));
    }

    @Override
    @Transactional
    public CommandProcessingResult create(final String json) {
        final MappingPayload payload = parse(json);
        mappingRepository.findByOfficeIdAndExternalSystemIgnoreCase(payload.officeId, payload.externalSystem).ifPresent(existing -> {
            throw new GeneralPlatformDomainRuleException("error.msg.external.organization.mapping.duplicate",
                    "A mapping already exists for office " + payload.officeId + " and system " + payload.externalSystem);
        });
        final ExternalOrganizationMapping mapping = mappingRepository.saveAndFlush(new ExternalOrganizationMapping(payload.officeId,
                payload.externalSystem, payload.externalId, payload.externalName, payload.currencyCode, payload.active));
        return CommandProcessingResult.resourceResult(mapping.getId(), null);
    }

    @Override
    @Transactional
    public CommandProcessingResult update(final Long mappingId, final String json) {
        final ExternalOrganizationMapping mapping = mapping(mappingId);
        final MappingPayload payload = parse(json);
        mappingRepository.findByOfficeIdAndExternalSystemIgnoreCase(payload.officeId, payload.externalSystem).ifPresent(existing -> {
            if (!existing.getId().equals(mappingId)) {
                throw new GeneralPlatformDomainRuleException("error.msg.external.organization.mapping.duplicate",
                        "A mapping already exists for office " + payload.officeId + " and system " + payload.externalSystem);
            }
        });
        mapping.update(payload.officeId, payload.externalSystem, payload.externalId, payload.externalName, payload.currencyCode,
                payload.active);
        mappingRepository.saveAndFlush(mapping);
        return CommandProcessingResult.resourceResult(mapping.getId(), null);
    }

    @Override
    @Transactional
    public CommandProcessingResult delete(final Long mappingId) {
        mappingRepository.delete(mapping(mappingId));
        return CommandProcessingResult.resourceResult(mappingId, null);
    }

    @Override
    @Transactional
    public ExternalOrganizationMappingImportResult importMappings(final String json) {
        final ExternalOrganizationMappingImportResult result = new ExternalOrganizationMappingImportResult();
        final JsonArray rows = importRows(json);
        if (rows == null || rows.size() == 0) {
            return result;
        }
        final Map<String, Office> officesByName = officeRepository.findAll().stream()
                .filter(office -> StringUtils.isNotBlank(office.getName()))
                .collect(Collectors.toMap(office -> office.getName().trim().toLowerCase(Locale.ROOT), office -> office, (a, b) -> a));
        for (int i = 0; i < rows.size(); i++) {
            final int rowNumber = i + 1;
            try {
                if (!rows.get(i).isJsonObject()) {
                    result.incrementSkipped();
                    continue;
                }
                final MappingPayload payload = parseImportRow(rows.get(i).getAsJsonObject(), officesByName);
                if (payload == null) {
                    result.incrementSkipped();
                    continue;
                }
                mappingRepository.findByOfficeIdAndExternalSystemIgnoreCase(payload.officeId, payload.externalSystem)
                        .ifPresentOrElse(existing -> {
                            existing.update(payload.officeId, payload.externalSystem, payload.externalId, payload.externalName,
                                    payload.currencyCode, payload.active);
                            mappingRepository.saveAndFlush(existing);
                            result.incrementUpdated();
                        }, () -> {
                            mappingRepository.saveAndFlush(new ExternalOrganizationMapping(payload.officeId, payload.externalSystem,
                                    payload.externalId, payload.externalName, payload.currencyCode, payload.active));
                            result.incrementCreated();
                        });
            } catch (final RuntimeException ex) {
                result.addError("Row " + rowNumber + ": " + ex.getMessage());
            }
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, ExternalOrganizationMapping> activeMappingsByOffice(final String externalSystem) {
        final Map<Long, ExternalOrganizationMapping> byOffice = new LinkedHashMap<>();
        if (StringUtils.isBlank(externalSystem)) {
            return byOffice;
        }
        for (final ExternalOrganizationMapping mapping : mappingRepository
                .findByExternalSystemIgnoreCaseAndActiveTrue(externalSystem.trim())) {
            byOffice.put(mapping.getOfficeId(), mapping);
        }
        return byOffice;
    }

    private ExternalOrganizationMapping mapping(final Long mappingId) {
        return mappingRepository.findById(mappingId).orElseThrow(() -> new ExternalOrganizationMappingNotFoundException(mappingId));
    }

    private ExternalOrganizationMappingData toData(final ExternalOrganizationMapping mapping) {
        final String officeName = officeRepository.findById(mapping.getOfficeId()).map(Office::getName).orElse(null);
        return new ExternalOrganizationMappingData(mapping.getId(), mapping.getOfficeId(), officeName, mapping.getExternalSystem(),
                mapping.getExternalId(), mapping.getExternalName(), mapping.getCurrencyCode(), mapping.isActive());
    }

    private MappingPayload parse(final String json) {
        if (StringUtils.isBlank(json)) {
            throw new GeneralPlatformDomainRuleException("error.msg.external.organization.mapping.json.required",
                    "Request body is required");
        }
        return parseElement(fromJsonHelper.parse(json), null);
    }

    private JsonArray importRows(final String json) {
        if (StringUtils.isBlank(json)) {
            throw new GeneralPlatformDomainRuleException("error.msg.external.organization.mapping.json.required",
                    "Request body is required");
        }
        final JsonElement root = fromJsonHelper.parse(json);
        if (root.isJsonArray()) {
            return root.getAsJsonArray();
        }
        if (root.isJsonObject() && root.getAsJsonObject().has("mappings")) {
            return fromJsonHelper.extractJsonArrayNamed("mappings", root);
        }
        throw new GeneralPlatformDomainRuleException("error.msg.external.organization.mapping.import.invalid",
                "Import body must be a JSON array or {\"mappings\": [...]}");
    }

    private MappingPayload parseImportRow(final JsonObject row, final Map<String, Office> officesByName) {
        final String externalId = fromJsonHelper.extractStringNamed("externalId", row);
        final String externalName = fromJsonHelper.extractStringNamed("externalName", row);
        if (StringUtils.isBlank(externalId) || StringUtils.isBlank(externalName)) {
            return null;
        }
        return parseElement(row, officesByName);
    }

    private MappingPayload parseElement(final JsonElement element, final Map<String, Office> officesByName) {
        Long officeId = fromJsonHelper.extractLongNamed("officeId", element);
        if (officeId == null && officesByName != null) {
            final String officeName = fromJsonHelper.extractStringNamed("officeName", element);
            if (StringUtils.isNotBlank(officeName)) {
                final Office office = officesByName.get(officeName.trim().toLowerCase(Locale.ROOT));
                if (office != null) {
                    officeId = office.getId();
                }
            }
        }
        if (officeId == null) {
            throw new GeneralPlatformDomainRuleException("error.msg.external.organization.mapping.office.required", "Office is required");
        }
        final Long resolvedOfficeId = officeId;
        officeRepository.findById(resolvedOfficeId).orElseThrow(() -> new OfficeNotFoundException(resolvedOfficeId));
        final String externalSystem = requiredText(element, "externalSystem", "External system is required");
        final String externalId = requiredText(element, "externalId", "External organization id is required");
        final String externalName = requiredText(element, "externalName", "External organization name is required");
        String currencyCode = fromJsonHelper.extractStringNamed("currencyCode", element);
        if (currencyCode != null) {
            currencyCode = currencyCode.trim();
            if (currencyCode.isEmpty()) {
                currencyCode = null;
            }
        }
        boolean active = true;
        if (fromJsonHelper.parameterExists("active", element)) {
            final Boolean parsed = fromJsonHelper.extractBooleanNamed("active", element);
            active = parsed == null || parsed;
        }
        return new MappingPayload(officeId, externalSystem, externalId, externalName, currencyCode, active);
    }

    private String requiredText(final JsonElement element, final String parameter, final String message) {
        final String value = fromJsonHelper.extractStringNamed(parameter, element);
        if (StringUtils.isBlank(value)) {
            throw new GeneralPlatformDomainRuleException("error.msg.external.organization.mapping." + parameter + ".required", message);
        }
        return value.trim();
    }

    private static final class MappingPayload {

        private final Long officeId;
        private final String externalSystem;
        private final String externalId;
        private final String externalName;
        private final String currencyCode;
        private final boolean active;

        private MappingPayload(final Long officeId, final String externalSystem, final String externalId, final String externalName,
                final String currencyCode, final boolean active) {
            this.officeId = officeId;
            this.externalSystem = externalSystem;
            this.externalId = externalId;
            this.externalName = externalName;
            this.currencyCode = currencyCode;
            this.active = active;
        }
    }
}
