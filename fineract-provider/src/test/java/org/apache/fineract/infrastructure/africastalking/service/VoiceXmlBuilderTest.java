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

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VoiceXmlBuilderTest {

    @Test
    void buildsMainMenuWithGetDigits() {
        final String xml = VoiceXmlBuilder.buildMainMenu();
        assertTrue(xml.contains("<GetDigits"));
        assertTrue(xml.contains("Press 1 for Loans"));
    }

    @Test
    void buildsDialResponse() {
        final String xml = VoiceXmlBuilder.buildDial("+254700000001");
        assertTrue(xml.contains("phoneNumbers=\"+254700000001\""));
        assertTrue(xml.contains("<Dial"));
    }

    @Test
    void buildsAfterHoursVoicemail() {
        final String xml = VoiceXmlBuilder.buildAfterHoursVoicemail();
        assertTrue(xml.contains("<Record"));
        assertTrue(xml.contains("currently closed"));
    }
}
