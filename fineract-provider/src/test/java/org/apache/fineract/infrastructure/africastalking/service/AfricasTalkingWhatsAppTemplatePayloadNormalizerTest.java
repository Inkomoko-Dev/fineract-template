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
package org.apache.fineract.infrastructure.africastalking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class AfricasTalkingWhatsAppTemplatePayloadNormalizerTest {

    @Test
    void rewritesBodyExamplesToMatchPlaceholderCount() {
        final String input = """
                {
                  "name": "loan_repayment_received",
                  "language": "en",
                  "category": "UTILITY",
                  "components": {
                    "body": {
                      "type": "BODY",
                      "kind": "TEXT",
                      "text": "Hi {{1}}, we received {{2}} for account {{3}}. Outstanding {{4}}.",
                      "example": { "body_text": ["Sample body text"] }
                    }
                  }
                }
                """;

        final JsonObject normalized = JsonParser.parseString(AfricasTalkingWhatsAppTemplatePayloadNormalizer.normalize(input))
                .getAsJsonObject();
        final JsonArray bodyText = normalized.getAsJsonObject("components").getAsJsonObject("body").getAsJsonObject("example")
                .getAsJsonArray("body_text");

        assertEquals(4, bodyText.size());
        assertEquals("Jane Doe", bodyText.get(0).getAsString());
        assertEquals("1500.00", bodyText.get(1).getAsString());
        assertEquals("000012345", bodyText.get(2).getAsString());
        assertEquals("8500.00", bodyText.get(3).getAsString());
    }

    @Test
    void removesBodyExampleWhenNoPlaceholders() {
        final String input = """
                {
                  "name": "static_notice",
                  "language": "en",
                  "category": "UTILITY",
                  "components": {
                    "body": {
                      "type": "BODY",
                      "kind": "TEXT",
                      "text": "Thank you for banking with Inkomoko.",
                      "example": { "body_text": ["unused"] }
                    }
                  }
                }
                """;

        final JsonObject body = JsonParser.parseString(AfricasTalkingWhatsAppTemplatePayloadNormalizer.normalize(input)).getAsJsonObject()
                .getAsJsonObject("components").getAsJsonObject("body");
        assertFalse(body.has("example"));
    }
}
