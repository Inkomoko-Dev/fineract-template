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
package org.apache.fineract.infrastructure.campaigns.whatsapp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.apache.fineract.infrastructure.campaigns.whatsapp.constants.WhatsAppAtTemplateApprovalStatus;
import org.apache.fineract.infrastructure.campaigns.whatsapp.data.WhatsAppAtTemplateData;
import org.apache.fineract.infrastructure.campaigns.whatsapp.domain.WhatsAppAtTemplate;
import org.apache.fineract.infrastructure.campaigns.whatsapp.domain.WhatsAppAtTemplateRepository;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WhatsAppAtTemplateCatalogServiceTest {

    @Mock
    private WhatsAppAtTemplateRepository repository;

    private WhatsAppAtTemplateCatalogService service;

    @BeforeEach
    void setUp() {
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Africa/Nairobi", null));
        service = new WhatsAppAtTemplateCatalogService(repository, new FromJsonHelper());
    }

    @AfterEach
    void tearDown() {
        ThreadLocalContextUtil.clearTenant();
    }

    @Test
    void upsertInsertsPendingByDefault() {
        when(repository.findByNameAndLanguageCode("payment_due", "en")).thenReturn(Optional.empty());
        when(repository.save(any(WhatsAppAtTemplate.class))).thenAnswer(invocation -> invocation.getArgument(0));

        final WhatsAppAtTemplate saved = service.upsertFromCreateRequest(
                "{\"name\":\"payment_due\",\"language\":\"en\",\"category\":\"UTILITY\",\"components\":{}}");

        assertThat(saved.getApprovalStatus()).isEqualTo(WhatsAppAtTemplateApprovalStatus.PENDING);
        final ArgumentCaptor<WhatsAppAtTemplate> captor = ArgumentCaptor.forClass(WhatsAppAtTemplate.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("payment_due");
    }

    @Test
    void upsertMarksApprovedWhenRequested() {
        when(repository.findByNameAndLanguageCode("payment_due", "en")).thenReturn(Optional.empty());
        when(repository.save(any(WhatsAppAtTemplate.class))).thenAnswer(invocation -> invocation.getArgument(0));

        final WhatsAppAtTemplate saved = service.upsertFromCreateRequest(
                "{\"name\":\"payment_due\",\"language\":\"en\",\"category\":\"UTILITY\",\"markAsApproved\":true,\"components\":{}}");

        assertThat(saved.getApprovalStatus()).isEqualTo(WhatsAppAtTemplateApprovalStatus.APPROVED);
    }

    @Test
    void stripCatalogOnlyFieldsRemovesMarkAsApproved() {
        final String stripped = service.stripCatalogOnlyFields(
                "{\"name\":\"payment_due\",\"language\":\"en\",\"markAsApproved\":true,\"components\":{}}");
        assertThat(stripped).doesNotContain("markAsApproved");
        assertThat(stripped).contains("payment_due");
    }

    @Test
    void retrieveApprovedMapsOnlyApprovedRows() {
        final WhatsAppAtTemplate row = new WhatsAppAtTemplate("hello", "en", "UTILITY", WhatsAppAtTemplateApprovalStatus.APPROVED, null);
        when(repository.findByApprovalStatusOrderByNameAscLanguageCodeAsc(WhatsAppAtTemplateApprovalStatus.APPROVED))
                .thenReturn(List.of(row));

        final var options = service.retrieveApproved();

        assertThat(options).hasSize(1);
        final WhatsAppAtTemplateData data = options.iterator().next();
        assertThat(data.getName()).isEqualTo("hello");
        assertThat(data.getApprovalStatus()).isEqualTo("APPROVED");
        verify(repository, never()).save(any());
    }
}
