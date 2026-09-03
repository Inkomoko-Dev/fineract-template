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

public final class WhatsAppInteractiveMessages {

    private WhatsAppInteractiveMessages() {}

    public static String welcomeLanguagePrompt() {
        return "Welcome to Inkomoko.\n1. English\n2. Kinyarwanda";
    }

    public static String consentPrompt(final String languageCode) {
        if ("rw".equalsIgnoreCase(languageCode)) {
            return "Twemere ko twakohereza ubutumwa bwa WhatsApp. Andika YES kwemera.";
        }
        return "We would like to send you WhatsApp messages from Inkomoko. Reply YES to accept.";
    }

    public static String consentAccepted(final String languageCode) {
        return "rw".equalsIgnoreCase(languageCode) ? "Urakoze. Wemeye ubutumwa bwa WhatsApp." : "Thank you. WhatsApp consent recorded.";
    }

    public static String consentDeclined(final String languageCode) {
        return "rw".equalsIgnoreCase(languageCode) ? "Ntacyo twakohereje. Andika START igihe ushaka kongera."
                : "No messages will be sent. Reply START when you want to opt in again.";
    }

    public static String optOutConfirmation() {
        return "You have been unsubscribed from Inkomoko WhatsApp messages. Reply START to opt in again.";
    }

    public static String optInWelcome() {
        return "Welcome back to Inkomoko WhatsApp. Reply with your language choice:\n1. English\n2. Kinyarwanda";
    }

    public static String mainMenuHeader(final String languageCode) {
        if ("rw".equalsIgnoreCase(languageCode)) {
            return "Murakaza neza kuri Inkomoko. Hitamo:";
        }
        return "Welcome to Inkomoko. Please choose an option:";
    }

    public static String loanServicePending(final String languageCode) {
        return "rw".equalsIgnoreCase(languageCode) ? "Iyi serivisi izaza vuba. Hitamo 7 kuvugana n'umujyanama."
                : "Loan self-service requires authentication and will be available in the next release. Choose 7 to speak to an advisor.";
    }

    public static String contentPending(final String languageCode, final String topic) {
        return "rw".equalsIgnoreCase(languageCode) ? "Amakuru kuri " + topic + " azaboneka vuba."
                : "Information for " + topic + " will be available soon. Choose 7 to speak to an advisor.";
    }

    public static String advisorHandoff(final String languageCode) {
        return "rw".equalsIgnoreCase(languageCode) ? "Twohereje icyifuzo cyawe ku mujyanama. Muzasubizwa vuba."
                : "Your request has been logged for advisor follow-up. We will respond during business hours.";
    }

    public static String staffChannelStub() {
        return "Employee WhatsApp self-service is managed separately. Please contact your supervisor or IT support.";
    }

    public static String invalidSelection(final String languageCode) {
        return "rw".equalsIgnoreCase(languageCode) ? "Hitamo imibare iri mu menu." : "Please choose a valid menu option.";
    }

    public static String navigationHint(final String languageCode) {
        return "rw".equalsIgnoreCase(languageCode) ? "Andika MENU cyangwa 0 gusubira inyuma."
                : "Reply MENU for main menu or 0/BACK to go back.";
    }

    public static String otherEnquiryPrompt(final String languageCode) {
        return "rw".equalsIgnoreCase(languageCode) ? "Sobanura icyifuzo cyawe mu ncamake:"
                : "Please describe your enquiry in a short message:";
    }
}
