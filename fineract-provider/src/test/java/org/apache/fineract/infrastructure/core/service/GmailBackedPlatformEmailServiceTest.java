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
package org.apache.fineract.infrastructure.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import org.apache.fineract.infrastructure.configuration.service.ExternalServicesPropertiesReadPlatformService;
import org.apache.fineract.infrastructure.core.domain.EmailDetail;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

class GmailBackedPlatformEmailServiceTest {

    @ParameterizedTest
    @CsvSource({ "https://cbs.inkomoko.com/#, https://cbs.inkomoko.com", "https://cbs.inkomoko.com/#/, https://cbs.inkomoko.com",
            "https://cbs.inkomoko.com, https://cbs.inkomoko.com", "' https://test.inkomoko.com:1063/ ', https://test.inkomoko.com:1063",
            "https://uat.example.com/?baseApiUrl=https://api.example.com&tenantIdentifier=default#, https://uat.example.com/?baseApiUrl=https://api.example.com&tenantIdentifier=default" })
    void systemAccessLinkUsesConfiguredUiRootWithoutTheRoute(final String baseUrl, final String expected) {
        assertThat(GmailBackedPlatformEmailService.systemAccessLink(baseUrl)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "   ", "#", "/#" })
    void systemAccessLinkFallsBackToCbsWhenBaseUrlIsUnset(final String baseUrl) {
        assertThat(GmailBackedPlatformEmailService.systemAccessLink(baseUrl)).isEqualTo("https://cbs.inkomoko.com");
    }

    @Test
    void invitationEmailLinksToConfiguredCbsAndNeverTheUncertifiedWwwHost() {
        final EmailDetail email = sendInvitation("https://test.inkomoko.com:1063/#");

        assertThat(email.getBody()).contains("<a href=\"https://test.inkomoko.com:1063\">https://test.inkomoko.com:1063</a>")
                .doesNotContain("www.cbs.inkomoko.com");
        assertThat(email.getAddress()).isEqualTo("jane@example.com");
    }

    @Test
    void invitationEmailFallsBackToCbsWhenBaseUrlIsUnset() {
        final EmailDetail email = sendInvitation("");

        assertThat(email.getBody()).contains("<a href=\"https://cbs.inkomoko.com\">https://cbs.inkomoko.com</a>")
                .doesNotContain("www.cbs.inkomoko.com");
    }

    @Test
    void invitationEmailEscapesTheConfiguredLink() {
        final EmailDetail email = sendInvitation("https://uat.example.com/?baseApiUrl=https://api.example.com&tenantIdentifier=default#");

        assertThat(email.getBody())
                .contains("href=\"https://uat.example.com/?baseApiUrl=https://api.example.com&amp;tenantIdentifier=default\"");
    }

    private static EmailDetail sendInvitation(final String baseUrl) {
        final GmailBackedPlatformEmailService service = spy(
                new GmailBackedPlatformEmailService(Mockito.mock(ExternalServicesPropertiesReadPlatformService.class)));
        ReflectionTestUtils.setField(service, "baseUrl", baseUrl);
        doNothing().when(service).sendDefinedEmail(any());

        service.sendToUserAccount("Head Office", "Jane", "jane@example.com", "jane.doe", "S3cret!pw");

        final ArgumentCaptor<EmailDetail> captor = ArgumentCaptor.forClass(EmailDetail.class);
        verify(service).sendDefinedEmail(captor.capture());
        return captor.getValue();
    }
}
