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
package org.apache.fineract.portfolio.supplier.data;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.apache.fineract.infrastructure.core.exception.InvalidJsonException;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.exception.UnsupportedParameterException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SupplierDataValidatorTest {

    private SupplierDataValidator validator;

    private static final String VALID_PAYLOAD = """
            {
              "sourceSystem":"KIFIYA",
              "externalId":"SUP-001",
              "name":"Abebe Kebede Trading PLC",
              "displayName":"Abebe Kebede",
              "businessLicenseNumber":"BL-998877",
              "supplierType":"Exclusive",
              "businessSector":"FMCG",
              "category":"TECHNOLOGY_AND_ELECTRONICS",
              "country":"Ethiopia",
              "tin":"1234567891",
              "status":"ACTIVE"
            }
            """;

    @BeforeEach
    void setUp() {
        this.validator = new SupplierDataValidator(new FromJsonHelper());
    }

    @Test
    void blankJsonFails() {
        assertThrows(InvalidJsonException.class, () -> validator.validateForUpsert(""));
        assertThrows(InvalidJsonException.class, () -> validator.validateForUpsert("   "));
        assertThrows(InvalidJsonException.class, () -> validator.validateForUpsert(null));
    }

    @Test
    void missingRequiredFieldsFail() {
        assertThrows(PlatformApiDataValidationException.class, () -> validator.validateForUpsert("{}"));
        assertThrows(PlatformApiDataValidationException.class,
                () -> validator.validateForUpsert("{\"externalId\":\"SUP-1\",\"name\":\"Abebe\"}"));
        assertThrows(PlatformApiDataValidationException.class,
                () -> validator.validateForUpsert("{\"sourceSystem\":\"KIFIYA\",\"name\":\"Abebe\"}"));
        assertThrows(PlatformApiDataValidationException.class,
                () -> validator.validateForUpsert("{\"sourceSystem\":\"KIFIYA\",\"externalId\":\"SUP-1\"}"));
    }

    @Test
    void missingProfileFieldsFail() {
        assertThrows(PlatformApiDataValidationException.class, () -> validator.validateForUpsert(
                "{\"sourceSystem\":\"KIFIYA\",\"externalId\":\"SUP-001\",\"name\":\"Abebe Kebede Trading PLC\"}"));
        assertThrows(PlatformApiDataValidationException.class, () -> validator.validateForUpsert(
                "{\"sourceSystem\":\"KIFIYA\",\"externalId\":\"SUP-001\",\"name\":\"Abebe\",\"displayName\":\"Abebe\",\"businessLicenseNumber\":\"BL-1\",\"supplierType\":\"Exclusive\",\"businessSector\":\"FMCG\",\"category\":\"Cat\",\"country\":\"Ethiopia\"}"));
    }

    @Test
    void blankRequiredFieldsFail() {
        assertThrows(PlatformApiDataValidationException.class, () -> validator
                .validateForUpsert("{\"sourceSystem\":\"  \",\"externalId\":\"SUP-1\",\"name\":\"Abebe\"}"));
        assertThrows(PlatformApiDataValidationException.class, () -> validator
                .validateForUpsert("{\"sourceSystem\":\"KIFIYA\",\"externalId\":\"\",\"name\":\"Abebe\"}"));
        assertThrows(PlatformApiDataValidationException.class, () -> validator
                .validateForUpsert("{\"sourceSystem\":\"KIFIYA\",\"externalId\":\"SUP-1\",\"name\":\"   \"}"));
        assertThrows(PlatformApiDataValidationException.class, () -> validator.validateForUpsert(
                "{\"sourceSystem\":\"KIFIYA\",\"externalId\":\"SUP-1\",\"name\":\"Abebe\",\"displayName\":\"\",\"businessLicenseNumber\":\"BL-1\",\"supplierType\":\"Exclusive\",\"businessSector\":\"FMCG\",\"category\":\"Cat\",\"country\":\"Ethiopia\",\"tin\":\"123\"}"));
    }

    @Test
    void fullValidPayloadPasses() {
        assertDoesNotThrow(() -> validator.validateForUpsert(VALID_PAYLOAD));
    }

    @Test
    void statusOmittedPasses() {
        assertDoesNotThrow(() -> validator.validateForUpsert(
                "{\"sourceSystem\":\"KIFIYA\",\"externalId\":\"SUP-001\",\"name\":\"Abebe Kebede Trading PLC\",\"displayName\":\"Abebe Kebede\",\"businessLicenseNumber\":\"BL-998877\",\"supplierType\":\"Exclusive\",\"businessSector\":\"FMCG\",\"category\":\"TECHNOLOGY_AND_ELECTRONICS\",\"country\":\"Ethiopia\",\"tin\":\"1234567891\"}"));
    }

    @Test
    void validStatusPasses() {
        assertDoesNotThrow(() -> validator.validateForUpsert(
                "{\"sourceSystem\":\"KIFIYA\",\"externalId\":\"SUP-001\",\"name\":\"Abebe Kebede Trading PLC\",\"displayName\":\"Abebe Kebede\",\"businessLicenseNumber\":\"BL-998877\",\"supplierType\":\"Exclusive\",\"businessSector\":\"FMCG\",\"category\":\"TECHNOLOGY_AND_ELECTRONICS\",\"country\":\"Ethiopia\",\"tin\":\"1234567891\",\"status\":\"ACTIVE\"}"));
        assertDoesNotThrow(() -> validator.validateForUpsert(
                "{\"sourceSystem\":\"KIFIYA\",\"externalId\":\"SUP-001\",\"name\":\"Abebe Kebede Trading PLC\",\"displayName\":\"Abebe Kebede\",\"businessLicenseNumber\":\"BL-998877\",\"supplierType\":\"Exclusive\",\"businessSector\":\"FMCG\",\"category\":\"TECHNOLOGY_AND_ELECTRONICS\",\"country\":\"Ethiopia\",\"tin\":\"1234567891\",\"status\":\"inactive\"}"));
    }

    @Test
    void invalidStatusFails() {
        assertThrows(PlatformApiDataValidationException.class, () -> validator.validateForUpsert(
                "{\"sourceSystem\":\"KIFIYA\",\"externalId\":\"SUP-001\",\"name\":\"Abebe Kebede Trading PLC\",\"displayName\":\"Abebe Kebede\",\"businessLicenseNumber\":\"BL-998877\",\"supplierType\":\"Exclusive\",\"businessSector\":\"FMCG\",\"category\":\"TECHNOLOGY_AND_ELECTRONICS\",\"country\":\"Ethiopia\",\"tin\":\"1234567891\",\"status\":\"PENDING\"}"));
    }

    @Test
    void overMaxLengthFails() {
        final String tooLongName = "x".repeat(256);
        assertThrows(PlatformApiDataValidationException.class, () -> validator.validateForUpsert(
                "{\"sourceSystem\":\"KIFIYA\",\"externalId\":\"SUP-001\",\"name\":\"" + tooLongName
                        + "\",\"displayName\":\"Abebe\",\"businessLicenseNumber\":\"BL-1\",\"supplierType\":\"Exclusive\",\"businessSector\":\"FMCG\",\"category\":\"Cat\",\"country\":\"Ethiopia\",\"tin\":\"123\"}"));
    }

    @Test
    void unsupportedParameterFails() {
        assertThrows(UnsupportedParameterException.class, () -> validator.validateForUpsert(
                "{\"sourceSystem\":\"KIFIYA\",\"externalId\":\"SUP-001\",\"name\":\"Abebe Kebede Trading PLC\",\"displayName\":\"Abebe Kebede\",\"businessLicenseNumber\":\"BL-998877\",\"supplierType\":\"Exclusive\",\"businessSector\":\"FMCG\",\"category\":\"TECHNOLOGY_AND_ELECTRONICS\",\"country\":\"Ethiopia\",\"tin\":\"1234567891\",\"unknownField\":1}"));
    }
}
