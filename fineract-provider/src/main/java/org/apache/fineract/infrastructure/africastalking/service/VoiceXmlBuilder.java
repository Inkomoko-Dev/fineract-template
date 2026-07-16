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

import org.apache.commons.lang3.StringUtils;

final class VoiceXmlBuilder {

    private VoiceXmlBuilder() {}

    static String buildMainMenu() {
        return wrap("<GetDigits timeout=\"10\" finishOnKey=\"#\">"
                + "<Say voice=\"woman\">Welcome to Inkomoko. Press 1 for Loans, 2 for Client Support, 3 for Internal Staff.</Say>"
                + "</GetDigits>");
    }

    static String buildDial(final String phoneNumber) {
        return wrap("<Dial phoneNumbers=\"" + escapeXmlAttribute(phoneNumber) + "\" record=\"true\" sequential=\"true\"/>");
    }

    static String buildAfterHoursVoicemail() {
        return wrap("<Say voice=\"woman\">Thank you for calling Inkomoko. Our office is currently closed. Please leave a message after the tone.</Say>"
                + "<Record finishOnKey=\"#\" maxLength=\"120\" playBeep=\"true\"/>");
    }

    static String buildUnavailableDepartment(final String departmentName) {
        return wrap("<Say voice=\"woman\">We're sorry, the " + escapeXmlText(departmentName)
                + " department is unavailable right now. Please try again later.</Say><Reject/>");
    }

    static String buildInvalidSelection() {
        return wrap("<Say voice=\"woman\">Invalid selection. Goodbye.</Say><Reject/>");
    }

    private static String wrap(final String inner) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Response>" + inner + "</Response>";
    }

    private static String escapeXmlAttribute(final String value) {
        return escapeXmlText(value);
    }

    private static String escapeXmlText(final String value) {
        if (StringUtils.isBlank(value)) {
            return "";
        }
        return value.replace("&", "&amp;").replace("\"", "&quot;").replace("'", "&apos;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
