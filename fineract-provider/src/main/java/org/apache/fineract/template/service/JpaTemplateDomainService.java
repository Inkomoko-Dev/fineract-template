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
package org.apache.fineract.template.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import java.util.ArrayList;
import java.util.List;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.exception.PlatformDataIntegrityException;
import org.apache.fineract.template.domain.Template;
import org.apache.fineract.template.domain.TemplateEntity;
import org.apache.fineract.template.domain.TemplateMapper;
import org.apache.fineract.template.domain.TemplateRepository;
import org.apache.fineract.template.domain.TemplateType;
import org.apache.fineract.template.exception.TemplateNotFoundException;
import org.apache.fineract.template.serialization.TemplateCommandFromApiJsonDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.NestedRuntimeException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JpaTemplateDomainService implements TemplateDomainService {

    private static final String PROPERTY_NAME = "name";
    private static final String PROPERTY_TEXT = "text";
    // private static final String PROPERTY_MAPPERS = "mappers";
    private static final String PROPERTY_ENTITY = "entity";
    private static final String PROPERTY_TYPE = "type";

    private static final Logger LOG = LoggerFactory.getLogger(JpaTemplateDomainService.class);

    @Autowired
    private TemplateRepository templateRepository;

    @Autowired
    private TemplateCommandFromApiJsonDeserializer fromApiJsonDeserializer;

    @Override
    public List<Template> getAll() {
        return this.templateRepository.findAll();
    }

    @Override
    public Template findOneById(final Long id) {
        return this.templateRepository.findById(id).orElseThrow(() -> new TemplateNotFoundException(id));
    }

    @Transactional
    @Override
    public CommandProcessingResult createTemplate(final JsonCommand command) {
        try {
            this.fromApiJsonDeserializer.validateForCreate(command.json());

            final Template template = Template.fromJson(command);
            LOG.debug("Creating template with name {}", template.getName());

            this.templateRepository.saveAndFlush(template);
            return new CommandProcessingResultBuilder().withEntityId(template.getId()).build();
        } catch (final JpaSystemException | DataIntegrityViolationException dve) {
            handleDataIntegrityIssues(command, dve);
            return CommandProcessingResult.empty();
        }
    }

    @Transactional
    @Override
    public CommandProcessingResult updateTemplate(final Long templateId, final JsonCommand command) {
        try {
            this.fromApiJsonDeserializer.validateForUpdate(command.json());

            final Template template = findOneById(templateId);
            template.setName(command.stringValueOfParameterNamed(PROPERTY_NAME));
            template.setText(command.stringValueOfParameterNamed(PROPERTY_TEXT));
            template.setEntity(TemplateEntity.fromInt(command.integerValueSansLocaleOfParameterNamed(PROPERTY_ENTITY)));
            template.setType(TemplateType.fromInt(command.integerValueSansLocaleOfParameterNamed(PROPERTY_TYPE)));

            final JsonArray array = command.arrayOfParameterNamed("mappers");
            final List<TemplateMapper> mappersList = new ArrayList<>();
            for (final JsonElement element : array) {
                mappersList.add(new TemplateMapper(element.getAsJsonObject().get("mappersorder").getAsInt(),
                        element.getAsJsonObject().get("mapperskey").getAsString(),
                        element.getAsJsonObject().get("mappersvalue").getAsString()));
            }
            template.setMappers(mappersList);

            this.templateRepository.saveAndFlush(template);

            return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(template.getId()).build();
        } catch (final JpaSystemException | DataIntegrityViolationException dve) {
            handleDataIntegrityIssues(command, dve);
            return CommandProcessingResult.empty();
        }
    }

    private void handleDataIntegrityIssues(final JsonCommand command, final NestedRuntimeException dve) {
        final Throwable realCause = dve.getMostSpecificCause();
        final String message = realCause.getMessage() == null ? "" : realCause.getMessage().toLowerCase();
        if (message.contains("unq_name") || message.contains("m_template.name") || message.contains("duplicate")) {
            final String name = command.stringValueOfParameterNamed(PROPERTY_NAME);
            throw new PlatformDataIntegrityException("error.msg.template.duplicate.name",
                    "A template with name `" + name + "` already exists", PROPERTY_NAME, name);
        }
        LOG.error("Template data integrity issue", dve);
        throw new PlatformDataIntegrityException("error.msg.template.unknown.data.integrity.issue",
                "Unknown data integrity issue with resource.");
    }

    @Transactional
    @Override
    public CommandProcessingResult removeTemplate(final Long templateId) {
        final Template template = findOneById(templateId);

        this.templateRepository.delete(template);

        return new CommandProcessingResultBuilder().withEntityId(templateId).build();
    }

    @Transactional
    @Override
    public Template updateTemplate(final Template template) {
        return this.templateRepository.saveAndFlush(template);
    }

    @Override
    public List<Template> getAllByEntityAndType(final TemplateEntity entity, final TemplateType type) {

        return this.templateRepository.findByEntityAndType(entity, type);
    }
}
