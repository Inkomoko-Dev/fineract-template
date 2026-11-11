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
package org.apache.fineract.infrastructure.campaigns.whatsapp.serialization;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.apache.fineract.infrastructure.campaigns.whatsapp.constants.WhatsAppMessageType;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WhatsAppCampaignValidatorTest {

    private WhatsAppCampaignValidator validator;

    @BeforeEach
    void setUp() {
        this.validator = new WhatsAppCampaignValidator(new FromJsonHelper());
    }

    @Test
    void validateCreate_templateRequiresTemplateFields() {
        final String json = """
                {
                  "campaignName":"T1",
                  "campaignType":3,
                  "triggerType":1,
                  "runReportId":179,
                  "messageType":"TEMPLATE",
                  "paramValue":{"officeId":"1","reportName":"Loan Repayment"},
                  "bodyVariableMapping":["firstname"],
                  "locale":"en",
                  "dateFormat":"dd MMMM yyyy"
                }
                """;
        assertThrows(PlatformApiDataValidationException.class, () -> this.validator.validateCreate(json));
    }

    @Test
    void validateCreate_freeformRequiresMessageAndAllowsMissingTemplateFields() {
        final String json = """
                {
                  "campaignName":"F1",
                  "campaignType":3,
                  "triggerType":1,
                  "runReportId":179,
                  "messageType":"FREEFORM",
                  "message":"Hello from freeform",
                  "paramValue":{"officeId":"1","reportName":"Loan Repayment"},
                  "bodyVariableMapping":[],
                  "locale":"en",
                  "dateFormat":"dd MMMM yyyy"
                }
                """;
        assertDoesNotThrow(() -> this.validator.validateCreate(json));
    }

    @Test
    void validateCreate_freeformRejectsBlankMessage() {
        final String json = """
                {
                  "campaignName":"F1",
                  "campaignType":3,
                  "triggerType":1,
                  "runReportId":179,
                  "messageType":"FREEFORM",
                  "message":"",
                  "paramValue":{"officeId":"1","reportName":"Loan Repayment"},
                  "bodyVariableMapping":[],
                  "locale":"en",
                  "dateFormat":"dd MMMM yyyy"
                }
                """;
        assertThrows(PlatformApiDataValidationException.class, () -> this.validator.validateCreate(json));
    }

    @Test
    void validatePreview_freeformRequiresMessage() {
        final String json = """
                {
                  "runReportId":179,
                  "messageType":"FREEFORM",
                  "message":"Preview hello",
                  "bodyVariableMapping":[],
                  "paramValue":{"officeId":"1","reportName":"Loan Repayment"}
                }
                """;
        assertDoesNotThrow(() -> this.validator.validatePreview(json));
    }

    @Test
    void validatePreview_templateRequiresTemplateFields() {
        final String json = """
                {
                  "runReportId":179,
                  "messageType":"TEMPLATE",
                  "bodyVariableMapping":[],
                  "paramValue":{"officeId":"1","reportName":"Loan Repayment"}
                }
                """;
        assertThrows(PlatformApiDataValidationException.class, () -> this.validator.validatePreview(json));
    }

    @Test
    void validateForUpdate_templateCannotClearAtTemplateName() {
        final String json = """
                {
                  "triggerType":1,
                  "atTemplateName":"",
                  "paramValue":{"officeId":"1","reportName":"Loan Repayment"}
                }
                """;
        assertThrows(PlatformApiDataValidationException.class,
                () -> this.validator.validateForUpdate(json, WhatsAppMessageType.TEMPLATE));
    }
}
