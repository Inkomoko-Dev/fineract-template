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
package org.apache.fineract.template.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

public class TemplateEnumTest {

    @Test
    public void templateTypeSmsResolvesByIdNotOrdinal() {
        assertEquals(2, TemplateType.SMS.getId());
        assertEquals(1, TemplateType.SMS.ordinal());
        assertEquals(TemplateType.SMS, TemplateType.fromInt(2));
    }

    @Test
    public void templateTypeDocumentResolvesById() {
        assertEquals(TemplateType.DOCUMENT, TemplateType.fromInt(0));
    }

    @Test
    public void templateTypeReturnsNullForUnknownAndNullIds() {
        assertNull(TemplateType.fromInt(1));
        assertNull(TemplateType.fromInt(99));
        assertNull(TemplateType.fromInt(null));
    }

    @Test
    public void templateEntityResolvesEveryDeclaredId() {
        assertEquals(TemplateEntity.CLIENT, TemplateEntity.fromInt(0));
        assertEquals(TemplateEntity.LOAN, TemplateEntity.fromInt(1));
        assertEquals(TemplateEntity.SAVING, TemplateEntity.fromInt(2));
        assertEquals(TemplateEntity.GROUP, TemplateEntity.fromInt(3));
    }

    @Test
    public void templateEntityReturnsNullForUnknownAndNullIds() {
        assertNull(TemplateEntity.fromInt(4));
        assertNull(TemplateEntity.fromInt(-1));
        assertNull(TemplateEntity.fromInt(null));
    }
}
