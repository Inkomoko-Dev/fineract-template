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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.apache.fineract.infrastructure.africastalking.config.AfricasTalkingProperties;
import org.apache.fineract.infrastructure.africastalking.domain.CommunicationMessage;
import org.apache.fineract.infrastructure.africastalking.domain.CommunicationMessageRepository;
import org.apache.fineract.infrastructure.africastalking.domain.CommunicationMessageStatus;
import org.apache.fineract.infrastructure.africastalking.domain.RecipientType;
import org.apache.fineract.infrastructure.africastalking.service.PhoneNumberNormalizer;
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
import org.apache.fineract.organisation.staff.domain.Staff;
import org.apache.fineract.organisation.staff.domain.StaffRepositoryWrapper;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.client.domain.ClientRepositoryWrapper;
import org.apache.fineract.portfolio.client.exception.ClientNotFoundException;
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
    @Mock
    private StaffRepositoryWrapper staffRepositoryWrapper;

    private WhatsAppCampaignWritePlatformServiceImpl service;

    @BeforeEach
    void setUp() {
        final AfricasTalkingProperties properties = new AfricasTalkingProperties();
        properties.getPhone().setDefaultCountryCode("254");
        service = new WhatsAppCampaignWritePlatformServiceImpl(context, whatsAppCampaignRepository, whatsAppCampaignValidator,
                reportRepository, fromJsonHelper, readReportingService, communicationMessageRepository, clientRepositoryWrapper,
                staffRepositoryWrapper, new PhoneNumberNormalizer(properties));
    }

    private WhatsAppCampaign clientCampaign(final String bodyVariableMapping) {
        final WhatsAppCampaign campaign = org.mockito.Mockito.mock(WhatsAppCampaign.class);
        lenient().when(campaign.getId()).thenReturn(42L);
        lenient().when(campaign.getParamValue()).thenReturn("{\"reportName\":\"Client Listing\",\"R_officeId\":\"1\"}");
        lenient().when(campaign.getAtTemplateName()).thenReturn("payment_due_today");
        lenient().when(campaign.getLanguageCode()).thenReturn("en");
        lenient().when(campaign.getBodyVariableMapping()).thenReturn(bodyVariableMapping);
        lenient().when(campaign.getMessage()).thenReturn("Payment reminder");
        lenient().when(campaign.getRecipientType()).thenReturn(RecipientType.CLIENT);
        return campaign;
    }

    private void stubReport(final String reportName, final List<ResultsetColumnHeaderData> headers, final List<ResultsetRowData> rows) {
        lenient().when(readReportingService.retrieveGenericResultSetForSmsEmailCampaign(eq(reportName), eq("report"), any()))
                .thenReturn(new GenericResultsetData(headers, rows));
    }

    @Test
    void insertDirectCampaignIntoOutboundTable_savesPendingTemplateMessage() throws Exception {
        final WhatsAppCampaign campaign = clientCampaign("[\"clientName\",\"amount\"]");

        final List<ResultsetColumnHeaderData> headers = List.of(ResultsetColumnHeaderData.basic("id", "BIGINT"),
                ResultsetColumnHeaderData.basic("mobileNo", "VARCHAR"), ResultsetColumnHeaderData.basic("clientName", "VARCHAR"),
                ResultsetColumnHeaderData.basic("amount", "VARCHAR"));
        stubReport("Client Listing", headers, List.of(ResultsetRowData.create(List.of("7", "+254712345678", "John", "1000"))));

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
        final WhatsAppCampaign campaign = clientCampaign("[\"clientName\"]");

        final List<ResultsetColumnHeaderData> headers = List.of(ResultsetColumnHeaderData.basic("id", "BIGINT"),
                ResultsetColumnHeaderData.basic("mobileNo", "VARCHAR"), ResultsetColumnHeaderData.basic("clientName", "VARCHAR"));
        // Tab in a string value used to break Jackson parse of hand-built report JSON.
        stubReport("Client Listing", headers, List.of(ResultsetRowData.create(List.of("7", "+254712345678", "John\tDoe"))));

        final Client client = org.mockito.Mockito.mock(Client.class);
        when(clientRepositoryWrapper.findOneWithNotFoundDetection(7L)).thenReturn(client);

        service.insertDirectCampaignIntoOutboundTable(campaign);

        final ArgumentCaptor<CommunicationMessage> captor = ArgumentCaptor.forClass(CommunicationMessage.class);
        verify(communicationMessageRepository).save(captor.capture());
        assertEquals("[\"John\\tDoe\"]", captor.getValue().getTemplateBodyValues());
    }

    @Test
    void insertDirectCampaignIntoOutboundTable_resolvesStaffForEmployeeAudience() throws Exception {
        final WhatsAppCampaign campaign = org.mockito.Mockito.mock(WhatsAppCampaign.class);
        when(campaign.getId()).thenReturn(43L);
        when(campaign.getParamValue()).thenReturn("{\"reportName\":\"Staff Listing\",\"R_officeId\":\"1\"}");
        when(campaign.getAtTemplateName()).thenReturn("staff_notice");
        when(campaign.getLanguageCode()).thenReturn("en");
        when(campaign.getBodyVariableMapping()).thenReturn("[\"FullName\"]");
        when(campaign.getRecipientType()).thenReturn(RecipientType.STAFF);
        when(campaign.isStaffCampaign()).thenReturn(true);

        final List<ResultsetColumnHeaderData> headers = List.of(ResultsetColumnHeaderData.basic("id", "BIGINT"),
                ResultsetColumnHeaderData.basic("mobileNo", "VARCHAR"), ResultsetColumnHeaderData.basic("FullName", "VARCHAR"));
        stubReport("Staff Listing", headers, List.of(ResultsetRowData.create(List.of("11", "+254708881885", "Jane Auma"))));

        final Staff staff = org.mockito.Mockito.mock(Staff.class);
        when(staffRepositoryWrapper.findOneWithNotFoundDetection(11L)).thenReturn(staff);

        service.insertDirectCampaignIntoOutboundTable(campaign);

        final ArgumentCaptor<CommunicationMessage> captor = ArgumentCaptor.forClass(CommunicationMessage.class);
        verify(communicationMessageRepository).save(captor.capture());
        final CommunicationMessage saved = captor.getValue();

        assertEquals(RecipientType.STAFF, saved.getRecipientType());
        assertEquals(staff, saved.getStaff());
        assertNull(saved.getClient());
        assertEquals("+254708881885", saved.getPhoneNumber());
        verify(clientRepositoryWrapper, never()).findOneWithNotFoundDetection(any(Long.class));
    }

    @Test
    void insertDirectCampaignIntoOutboundTable_normalizesLocalFormatMobileNumbers() throws Exception {
        final WhatsAppCampaign campaign = clientCampaign("[\"clientName\"]");

        final List<ResultsetColumnHeaderData> headers = List.of(ResultsetColumnHeaderData.basic("id", "BIGINT"),
                ResultsetColumnHeaderData.basic("mobileNo", "VARCHAR"), ResultsetColumnHeaderData.basic("clientName", "VARCHAR"));
        stubReport("Client Listing", headers, List.of(ResultsetRowData.create(List.of("7", "0708881885", "John"))));

        when(clientRepositoryWrapper.findOneWithNotFoundDetection(7L)).thenReturn(org.mockito.Mockito.mock(Client.class));

        service.insertDirectCampaignIntoOutboundTable(campaign);

        final ArgumentCaptor<CommunicationMessage> captor = ArgumentCaptor.forClass(CommunicationMessage.class);
        verify(communicationMessageRepository).save(captor.capture());
        assertEquals("+254708881885", captor.getValue().getPhoneNumber());
    }

    @Test
    void insertDirectCampaignIntoOutboundTable_unresolvableRecipientDoesNotAbortRemainingRows() throws Exception {
        final WhatsAppCampaign campaign = clientCampaign("[\"clientName\"]");

        final List<ResultsetColumnHeaderData> headers = List.of(ResultsetColumnHeaderData.basic("id", "BIGINT"),
                ResultsetColumnHeaderData.basic("mobileNo", "VARCHAR"), ResultsetColumnHeaderData.basic("clientName", "VARCHAR"));
        stubReport("Client Listing", headers,
                List.of(ResultsetRowData.create(List.of("7", "+254712345678", "John")),
                        ResultsetRowData.create(List.of("8", "+254712345679", "Jane")),
                        ResultsetRowData.create(List.of("9", "+254712345670", "Joe"))));

        when(clientRepositoryWrapper.findOneWithNotFoundDetection(7L)).thenThrow(new ClientNotFoundException(7L));
        when(clientRepositoryWrapper.findOneWithNotFoundDetection(8L)).thenReturn(org.mockito.Mockito.mock(Client.class));
        when(clientRepositoryWrapper.findOneWithNotFoundDetection(9L)).thenReturn(org.mockito.Mockito.mock(Client.class));

        service.insertDirectCampaignIntoOutboundTable(campaign);

        final ArgumentCaptor<CommunicationMessage> captor = ArgumentCaptor.forClass(CommunicationMessage.class);
        verify(communicationMessageRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertEquals(List.of("+254712345679", "+254712345670"),
                captor.getAllValues().stream().map(CommunicationMessage::getPhoneNumber).toList());
    }

    @Test
    void insertDirectCampaignIntoOutboundTable_skipsRowWithUnresolvedTemplateVariable() throws Exception {
        final WhatsAppCampaign campaign = clientCampaign("[\"clientName\",\"amountDue\"]");

        final List<ResultsetColumnHeaderData> headers = List.of(ResultsetColumnHeaderData.basic("id", "BIGINT"),
                ResultsetColumnHeaderData.basic("mobileNo", "VARCHAR"), ResultsetColumnHeaderData.basic("clientName", "VARCHAR"));
        // The report never returns an "amountDue" column, so the Meta template variable cannot be filled.
        stubReport("Client Listing", headers, List.of(ResultsetRowData.create(List.of("7", "+254712345678", "John"))));

        service.insertDirectCampaignIntoOutboundTable(campaign);

        verify(communicationMessageRepository, never()).save(any(CommunicationMessage.class));
    }

    @Test
    void insertDirectCampaignIntoOutboundTable_skipsRowWithBlankMobileNumber() throws Exception {
        final WhatsAppCampaign campaign = clientCampaign("[\"clientName\"]");

        final List<ResultsetColumnHeaderData> headers = List.of(ResultsetColumnHeaderData.basic("id", "BIGINT"),
                ResultsetColumnHeaderData.basic("mobileNo", "VARCHAR"), ResultsetColumnHeaderData.basic("clientName", "VARCHAR"));
        stubReport("Client Listing", headers,
                List.of(ResultsetRowData.create(List.of("7", "", "John")), ResultsetRowData.create(List.of("8", "+254712345679", "Jane"))));

        when(clientRepositoryWrapper.findOneWithNotFoundDetection(8L)).thenReturn(org.mockito.Mockito.mock(Client.class));

        service.insertDirectCampaignIntoOutboundTable(campaign);

        final ArgumentCaptor<CommunicationMessage> captor = ArgumentCaptor.forClass(CommunicationMessage.class);
        verify(communicationMessageRepository).save(captor.capture());
        assertEquals("+254712345679", captor.getValue().getPhoneNumber());
    }
}
