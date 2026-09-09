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

import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.DataValidatorBuilder;
import org.apache.fineract.infrastructure.core.exception.InvalidJsonException;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.template.domain.TemplateEntity;
import org.apache.fineract.template.domain.TemplateType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public final class TemplateCommandFromApiJsonDeserializer {

    public static final String NAME = "name";
    public static final String TEXT = "text";
    public static final String ENTITY = "entity";
    public static final String TYPE = "type";
    public static final String MAPPERS = "mappers";

    private static final String RESOURCE_NAME = "template";
    private static final Integer NAME_MAX_LENGTH = 100;

    private final Set<String> supportedParameters = new HashSet<>(Arrays.asList(NAME, TEXT, ENTITY, TYPE, MAPPERS));

    private final FromJsonHelper fromApiJsonHelper;

    @Autowired
    public TemplateCommandFromApiJsonDeserializer(final FromJsonHelper fromApiJsonHelper) {
        this.fromApiJsonHelper = fromApiJsonHelper;
    }

    public void validateForCreate(final String json) {
        validate(json);
    }

    public void validateForUpdate(final String json) {
        validate(json);
    }

    private void validate(final String json) {
        if (StringUtils.isBlank(json)) {
            throw new InvalidJsonException();
        }

        final Type typeOfMap = new TypeToken<Map<String, Object>>() {}.getType();
        this.fromApiJsonHelper.checkForUnsupportedParameters(typeOfMap, json, this.supportedParameters);

        final JsonElement element = this.fromApiJsonHelper.parse(json);
        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors).resource(RESOURCE_NAME);

        final String name = this.fromApiJsonHelper.extractStringNamed(NAME, element);
        baseDataValidator.reset().parameter(NAME).value(name).notBlank().notExceedingLengthOf(NAME_MAX_LENGTH);

        final String text = this.fromApiJsonHelper.extractStringNamed(TEXT, element);
        baseDataValidator.reset().parameter(TEXT).value(text).notBlank();

        final Integer entityId = this.fromApiJsonHelper.extractIntegerSansLocaleNamed(ENTITY, element);
        baseDataValidator.reset().parameter(ENTITY).value(entityId).notNull();
        if (entityId != null && TemplateEntity.fromInt(entityId) == null) {
            baseDataValidator.reset().parameter(ENTITY).value(entityId).isOneOfTheseValues(entityIds());
        }

        final Integer typeId = this.fromApiJsonHelper.extractIntegerSansLocaleNamed(TYPE, element);
        baseDataValidator.reset().parameter(TYPE).value(typeId).notNull();
        if (typeId != null && TemplateType.fromInt(typeId) == null) {
            baseDataValidator.reset().parameter(TYPE).value(typeId).isOneOfTheseValues(typeIds());
        }

        if (!dataValidationErrors.isEmpty()) {
            throw new PlatformApiDataValidationException(dataValidationErrors);
        }
    }

    private Object[] entityIds() {
        final TemplateEntity[] entities = TemplateEntity.values();
        final Object[] ids = new Object[entities.length];
        for (int i = 0; i < entities.length; i++) {
            ids[i] = entities[i].getId();
        }
        return ids;
    }

    private Object[] typeIds() {
        final TemplateType[] types = TemplateType.values();
        final Object[] ids = new Object[types.length];
        for (int i = 0; i < types.length; i++) {
            ids[i] = types[i].getId();
        }
        return ids;
    }
}
