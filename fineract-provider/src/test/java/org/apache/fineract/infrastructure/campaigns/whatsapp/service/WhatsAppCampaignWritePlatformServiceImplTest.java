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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.apache.fineract.infrastructure.africastalking.domain.CommunicationMessage;
import org.apache.fineract.infrastructure.africastalking.domain.CommunicationMessageRepository;
import org.apache.fineract.infrastructure.africastalking.domain.CommunicationMessageStatus;
import org.apache.fineract.infrastructure.africastalking.domain.RecipientType;
import org.apache.fineract.infrastructure.campaigns.whatsapp.domain.WhatsAppCampaign;
import org.apache.fineract.infrastructure.campaigns.whatsapp.domain.WhatsAppCampaignRepository;
import org.apache.fineract.infrastructure.campaigns.whatsapp.serialization.WhatsAppCampaignValidator;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.dataqueries.data.GenericResultsetData;
import org.apache.fineract.infrastructure.dataqueries.data.ResultsetColumnHeaderData;
import org.apache.fineract.infrastructure.dataqueries.data.ResultsetRowData;
import org.apache.fineract.infrastructure.dataqueries.domain.ReportRepository;
import org.apache.fineract.infrastructure.dataqueries.service.ReadReportingService;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.client.domain.ClientRepositoryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WhatsAppCampaignWritePlatformServiceImplTest {

    @Mock
    private PlatformSecurityContext context;
    @Mock
    private WhatsAppCampaignRepository whatsAppCampaignRepository;
    @Mock
    private WhatsAppCampaignValidator whatsAppCampaignValidator;
    @Mock
    private ReportRepository reportRepository;
    @Mock
    private FromJsonHelper fromJsonHelper;
    @Mock
    private ReadReportingService readReportingService;
    @Mock
    private CommunicationMessageRepository communicationMessageRepository;
    @Mock
    private ClientRepositoryWrapper clientRepositoryWrapper;

    private WhatsAppCampaignWritePlatformServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new WhatsAppCampaignWritePlatformServiceImpl(context, whatsAppCampaignRepository, whatsAppCampaignValidator,
                reportRepository, fromJsonHelper, readReportingService, communicationMessageRepository, clientRepositoryWrapper);
    }

    @Test
    void insertDirectCampaignIntoOutboundTable_savesPendingTemplateMessage() throws Exception {
        final WhatsAppCampaign campaign = org.mockito.Mockito.mock(WhatsAppCampaign.class);
        when(campaign.getId()).thenReturn(42L);
        when(campaign.getParamValue()).thenReturn("{\"reportName\":\"Client Listing\",\"R_officeId\":\"1\"}");
        when(campaign.getAtTemplateName()).thenReturn("payment_due_today");
        when(campaign.getLanguageCode()).thenReturn("en");
        when(campaign.getBodyVariableMapping()).thenReturn("[\"clientName\",\"amount\"]");
        when(campaign.getMessage()).thenReturn("Payment reminder");

        final List<ResultsetColumnHeaderData> headers = List.of(ResultsetColumnHeaderData.basic("id", "BIGINT"),
                ResultsetColumnHeaderData.basic("mobileNo", "VARCHAR"), ResultsetColumnHeaderData.basic("clientName", "VARCHAR"),
                ResultsetColumnHeaderData.basic("amount", "VARCHAR"));
        final List<ResultsetRowData> rows = List.of(ResultsetRowData.create(List.of("7", "+254712345678", "John", "1000")));
        final GenericResultsetData resultset = new GenericResultsetData(headers, rows);
        when(readReportingService.retrieveGenericResultSetForSmsEmailCampaign(eq("Client Listing"), eq("report"), any()))
                .thenReturn(resultset);

        final Client client = org.mockito.Mockito.mock(Client.class);
        when(clientRepositoryWrapper.findOneWithNotFoundDetection(7L)).thenReturn(client);

        service.insertDirectCampaignIntoOutboundTable(campaign);

        final ArgumentCaptor<CommunicationMessage> captor = ArgumentCaptor.forClass(CommunicationMessage.class);
        verify(communicationMessageRepository).save(captor.capture());
        final CommunicationMessage saved = captor.getValue();

        assertEquals("+254712345678", saved.getPhoneNumber());
        assertEquals("payment_due_today", saved.getTemplateName());
        assertEquals("en", saved.getTemplateLanguage());
        assertEquals("[\"John\",\"1000\"]", saved.getTemplateBodyValues());
        assertEquals("John|1000", saved.getMessageBody());
        assertEquals(42L, saved.getCampaignId());
        assertEquals(RecipientType.CLIENT, saved.getRecipientType());
        assertEquals(client, saved.getClient());
        assertEquals(CommunicationMessageStatus.PENDING, saved.getStatus());
    }

    @Test
    void insertDirectCampaignIntoOutboundTable_toleratesTabsInReportValues() throws Exception {
        final WhatsAppCampaign campaign = org.mockito.Mockito.mock(WhatsAppCampaign.class);
        when(campaign.getId()).thenReturn(42L);
        when(campaign.getParamValue()).thenReturn("{\"reportName\":\"Client Listing\",\"R_officeId\":\"1\"}");
        when(campaign.getAtTemplateName()).thenReturn("payment_due_today");
        when(campaign.getLanguageCode()).thenReturn("en");
        when(campaign.getBodyVariableMapping()).thenReturn("[\"clientName\"]");
        when(campaign.getMessage()).thenReturn("Payment reminder");

        final List<ResultsetColumnHeaderData> headers = List.of(ResultsetColumnHeaderData.basic("id", "BIGINT"),
                ResultsetColumnHeaderData.basic("mobileNo", "VARCHAR"), ResultsetColumnHeaderData.basic("clientName", "VARCHAR"));
        // Tab in a string value used to break Jackson parse of hand-built report JSON.
        final List<ResultsetRowData> rows = List.of(ResultsetRowData.create(List.of("7", "+254712345678", "John\tDoe")));
        final GenericResultsetData resultset = new GenericResultsetData(headers, rows);
        when(readReportingService.retrieveGenericResultSetForSmsEmailCampaign(eq("Client Listing"), eq("report"), any()))
                .thenReturn(resultset);

        final Client client = org.mockito.Mockito.mock(Client.class);
        when(clientRepositoryWrapper.findOneWithNotFoundDetection(7L)).thenReturn(client);

        service.insertDirectCampaignIntoOutboundTable(campaign);

        final ArgumentCaptor<CommunicationMessage> captor = ArgumentCaptor.forClass(CommunicationMessage.class);
        verify(communicationMessageRepository).save(captor.capture());
        assertEquals("[\"John\\tDoe\"]", captor.getValue().getTemplateBodyValues());
    }
}
