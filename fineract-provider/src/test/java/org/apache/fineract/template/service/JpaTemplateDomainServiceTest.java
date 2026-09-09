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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gson.JsonElement;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.exception.PlatformDataIntegrityException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.template.domain.Template;
import org.apache.fineract.template.domain.TemplateRepository;
import org.apache.fineract.template.serialization.TemplateCommandFromApiJsonDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.test.util.ReflectionTestUtils;

public class JpaTemplateDomainServiceTest {

    private static final String VALID_JSON = "{\"name\":\"Welcome\",\"text\":\"Hello\",\"entity\":0,\"type\":0,\"mappers\":[]}";

    private TemplateRepository templateRepository;
    private JpaTemplateDomainService service;
    private FromJsonHelper fromJsonHelper;

    @BeforeEach
    public void setUp() {
        this.fromJsonHelper = new FromJsonHelper();
        this.templateRepository = Mockito.mock(TemplateRepository.class);
        this.service = new JpaTemplateDomainService();
        ReflectionTestUtils.setField(this.service, "templateRepository", this.templateRepository);
        ReflectionTestUtils.setField(this.service, "fromApiJsonDeserializer",
                new TemplateCommandFromApiJsonDeserializer(this.fromJsonHelper));
    }

    private JsonCommand command(final String json) {
        final JsonElement parsed = this.fromJsonHelper.parse(json);
        return JsonCommand.from(json, parsed, this.fromJsonHelper, "template", null, null, null, null, null, null, null, null, null, null,
                null);
    }

    @Test
    public void duplicateNameIsReportedAsDuplicateNotAsServerError() {
        when(this.templateRepository.saveAndFlush(any(Template.class)))
                .thenThrow(new JpaSystemException(new RuntimeException("Duplicate entry 'Welcome' for key 'm_template.name'")));

        final PlatformDataIntegrityException exception = assertThrows(PlatformDataIntegrityException.class,
                () -> this.service.createTemplate(command(VALID_JSON)));

        assertEquals("error.msg.template.duplicate.name", exception.getGlobalisationMessageCode());
        assertEquals("name", exception.getParameterName());
    }

    @Test
    public void duplicateNameIsReportedWhenTheConstraintIsNamedUnqName() {
        when(this.templateRepository.saveAndFlush(any(Template.class)))
                .thenThrow(new JpaSystemException(new RuntimeException("duplicate key value violates unique constraint \"unq_name\"")));

        final PlatformDataIntegrityException exception = assertThrows(PlatformDataIntegrityException.class,
                () -> this.service.createTemplate(command(VALID_JSON)));

        assertEquals("error.msg.template.duplicate.name", exception.getGlobalisationMessageCode());
    }

    @Test
    public void otherIntegrityFailuresAreReportedAsUnknownIntegrityIssue() {
        when(this.templateRepository.saveAndFlush(any(Template.class)))
                .thenThrow(new JpaSystemException(new RuntimeException("Column 'text' cannot be null")));

        final PlatformDataIntegrityException exception = assertThrows(PlatformDataIntegrityException.class,
                () -> this.service.createTemplate(command(VALID_JSON)));

        assertEquals("error.msg.template.unknown.data.integrity.issue", exception.getGlobalisationMessageCode());
    }

    @Test
    public void invalidPayloadIsRejectedBeforeAnySave() {
        assertThrows(PlatformApiDataValidationException.class,
                () -> this.service.createTemplate(command("{\"name\":\"\",\"text\":\"\",\"mappers\":[]}")));

        verify(this.templateRepository, never()).saveAndFlush(any(Template.class));
    }
}
