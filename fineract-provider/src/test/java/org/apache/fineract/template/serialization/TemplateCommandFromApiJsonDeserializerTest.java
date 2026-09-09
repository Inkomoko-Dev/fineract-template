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
package org.apache.fineract.template.serialization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.exception.InvalidJsonException;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TemplateCommandFromApiJsonDeserializerTest {

    private TemplateCommandFromApiJsonDeserializer deserializer;

    @BeforeEach
    public void setUp() {
        this.deserializer = new TemplateCommandFromApiJsonDeserializer(new FromJsonHelper());
    }

    private static String json(final String name, final String text, final String entity, final String type) {
        return "{\"name\":" + name + ",\"text\":" + text + ",\"entity\":" + entity + ",\"type\":" + type + ",\"mappers\":[]}";
    }

    private List<ApiParameterError> errorsFrom(final String payload) {
        final PlatformApiDataValidationException exception = assertThrows(PlatformApiDataValidationException.class,
                () -> this.deserializer.validateForCreate(payload));
        return exception.getErrors();
    }

    private static boolean hasErrorFor(final List<ApiParameterError> errors, final String parameterName) {
        return errors.stream().anyMatch(error -> parameterName.equals(error.getParameterName()));
    }

    @Test
    public void validPayloadPasses() {
        this.deserializer.validateForCreate(json("\"Welcome letter\"", "\"Hello {{client.displayName}}\"", "0", "0"));
    }

    @Test
    public void smsTypeIdTwoIsAccepted() {
        this.deserializer.validateForCreate(json("\"Reminder\"", "\"Due today\"", "0", "2"));
    }

    @Test
    public void blankJsonIsRejected() {
        assertThrows(InvalidJsonException.class, () -> this.deserializer.validateForCreate(""));
    }

    @Test
    public void missingNameIsRejected() {
        final List<ApiParameterError> errors = errorsFrom("{\"text\":\"body\",\"entity\":0,\"type\":0,\"mappers\":[]}");
        assertTrue(hasErrorFor(errors, "name"));
    }

    @Test
    public void blankNameIsRejected() {
        final List<ApiParameterError> errors = errorsFrom(json("\"   \"", "\"body\"", "0", "0"));
        assertTrue(hasErrorFor(errors, "name"));
    }

    @Test
    public void overlongNameIsRejected() {
        final String longName = "\"" + "x".repeat(101) + "\"";
        final List<ApiParameterError> errors = errorsFrom(json(longName, "\"body\"", "0", "0"));
        assertTrue(hasErrorFor(errors, "name"));
    }

    @Test
    public void missingTextIsRejected() {
        final List<ApiParameterError> errors = errorsFrom("{\"name\":\"n\",\"entity\":0,\"type\":0,\"mappers\":[]}");
        assertTrue(hasErrorFor(errors, "text"));
    }

    @Test
    public void blankTextIsRejected() {
        final List<ApiParameterError> errors = errorsFrom(json("\"n\"", "\"\"", "0", "0"));
        assertTrue(hasErrorFor(errors, "text"));
    }

    @Test
    public void missingEntityIsRejected() {
        final List<ApiParameterError> errors = errorsFrom("{\"name\":\"n\",\"text\":\"body\",\"type\":0,\"mappers\":[]}");
        assertTrue(hasErrorFor(errors, "entity"));
    }

    @Test
    public void unknownEntityIsRejected() {
        final List<ApiParameterError> errors = errorsFrom(json("\"n\"", "\"body\"", "9", "0"));
        assertTrue(hasErrorFor(errors, "entity"));
    }

    @Test
    public void missingTypeIsRejected() {
        final List<ApiParameterError> errors = errorsFrom("{\"name\":\"n\",\"text\":\"body\",\"entity\":0,\"mappers\":[]}");
        assertTrue(hasErrorFor(errors, "type"));
    }

    @Test
    public void unknownTypeIsRejected() {
        final List<ApiParameterError> errors = errorsFrom(json("\"n\"", "\"body\"", "0", "1"));
        assertTrue(hasErrorFor(errors, "type"));
    }

    @Test
    public void everyMissingFieldIsReportedTogether() {
        final List<ApiParameterError> errors = errorsFrom("{\"mappers\":[]}");
        assertEquals(4, errors.size());
        assertTrue(hasErrorFor(errors, "name"));
        assertTrue(hasErrorFor(errors, "text"));
        assertTrue(hasErrorFor(errors, "entity"));
        assertTrue(hasErrorFor(errors, "type"));
    }
}
