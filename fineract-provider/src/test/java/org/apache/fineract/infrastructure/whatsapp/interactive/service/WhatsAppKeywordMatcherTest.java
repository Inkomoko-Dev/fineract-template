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
package org.apache.fineract.infrastructure.whatsapp.interactive.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.fineract.infrastructure.whatsapp.interactive.config.WhatsAppInteractiveProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WhatsAppKeywordMatcherTest {

    private WhatsAppKeywordMatcher matcher;
    private WhatsAppInteractiveProperties properties;

    @BeforeEach
    void setUp() {
        properties = new WhatsAppInteractiveProperties();
        properties.setOptOutKeywords("STOP,UNSUBSCRIBE");
        properties.setConsentAcceptKeywords("YES,AGREE");
        properties.setMainMenuKeywords("MENU,MAIN");
        properties.setBackMenuKeywords("BACK,0");
        matcher = new WhatsAppKeywordMatcher(properties);
    }

    @Test
    void matchesOptOutKeyword() {
        assertTrue(matcher.matchesOptOut("STOP"));
        assertTrue(matcher.matchesOptOut("unsubscribe"));
    }

    @Test
    void matchesConsentAccept() {
        assertTrue(matcher.matchesConsentAccept("YES"));
        assertFalse(matcher.matchesConsentAccept("NO"));
    }

    @Test
    void matchesNavigationKeywords() {
        assertTrue(matcher.matchesMainMenu("MENU"));
        assertTrue(matcher.matchesBackMenu("BACK"));
    }
}
