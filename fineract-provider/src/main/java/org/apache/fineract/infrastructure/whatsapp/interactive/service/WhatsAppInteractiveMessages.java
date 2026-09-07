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

import java.math.BigDecimal;
import java.time.LocalDate;
import org.apache.commons.lang3.StringUtils;

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

    public static String advisorHandoff(final String languageCode, final String ticketNumber, final boolean withinBusinessHours,
            final String businessHoursSummary) {
        if ("rw".equalsIgnoreCase(languageCode)) {
            final String hours = StringUtils.isNotBlank(businessHoursSummary) ? businessHoursSummary : "amasaha y'akazi";
            if (withinBusinessHours) {
                return "Twohereje icyifuzo cyawe. Nimero y'itike: " + ticketNumber + ". Umujyanama azasubiza vuba.";
            }
            return "Twohereje icyifuzo cyawe. Nimero y'itike: " + ticketNumber + ". Tuzasubiza mu masaha y'akazi (" + hours + ").";
        }
        final String hours = StringUtils.isNotBlank(businessHoursSummary) ? businessHoursSummary : "business hours";
        if (withinBusinessHours) {
            return "Your request has been logged. Ticket " + ticketNumber + ". An advisor will respond shortly.";
        }
        return "Your request has been logged. Ticket " + ticketNumber + ". We will respond during business hours (" + hours + ").";
    }

    public static String advisorHandoff(final String languageCode) {
        return advisorHandoff(languageCode, "pending", true, null);
    }

    public static String staffChannelStub() {
        return "Employee WhatsApp self-service is currently disabled. Please contact your supervisor or IT support.";
    }

    public static String staffAccessDenied(final String languageCode) {
        return "rw".equalsIgnoreCase(languageCode) ? "Ntabwo wemerewe gukoresha iyi serivisi. Vugana n'umuyobozi wawe."
                : "You are not authorized to use employee WhatsApp self-service. Contact your supervisor.";
    }

    public static String staffOptInWelcome() {
        return "Welcome to Inkomoko employee WhatsApp. Reply with your language choice:\n1. English\n2. Kinyarwanda";
    }

    public static String staffLoanNotAvailable(final String languageCode) {
        return "rw".equalsIgnoreCase(languageCode) ? "Iyi serivisi ntiboneka kuri abakozi b'Inkomoko."
                : "That client loan self-service option is not available on the employee WhatsApp channel.";
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

    public static String confirmIdentityPrompt(final String languageCode, final String displayName) {
        return "rw".equalsIgnoreCase(languageCode) ? "Twemeza ko uri " + displayName + ". Andika 1 kwemeza cyangwa andika nimero ya konti yawe."
                : "We found your profile as " + displayName + ". Reply 1 to confirm or enter your client account number.";
    }

    public static String enterClientAccountPrompt(final String languageCode) {
        return "rw".equalsIgnoreCase(languageCode) ? "Andika nimero ya konti yawe ya Inkomoko:"
                : "Enter your Inkomoko client account number to continue:";
    }

    public static String identityNotFound(final String languageCode) {
        return "rw".equalsIgnoreCase(languageCode) ? "Konti ntiyabonetse. Ongera ugerageze cyangwa uvugane n'umujyanama."
                : "We could not verify your identity. Try again or speak to an advisor.";
    }

    public static String otpIssued(final String languageCode, final String otp) {
        return "rw".equalsIgnoreCase(languageCode) ? "Kode y'igenzura: " + otp + ". Irangira mu " + "5" + " iminota."
                : "Your verification code is " + otp + ". It expires in 5 minutes.";
    }

    public static String otpInvalid(final String languageCode) {
        return "rw".equalsIgnoreCase(languageCode) ? "Kode siyo. Ongera ugerageze."
                : "Invalid or expired verification code. Please try again.";
    }

    public static String authSuccess(final String languageCode) {
        return "rw".equalsIgnoreCase(languageCode) ? "Wemejwe neza." : "Authentication successful.";
    }

    public static String loanSelectionHeader(final String languageCode) {
        return "rw".equalsIgnoreCase(languageCode) ? "Hitamo inguzanyo:" : "Select a loan:";
    }

    public static String noActiveLoans(final String languageCode) {
        return "rw".equalsIgnoreCase(languageCode) ? "Nta nguzanyo ikora yabonetse." : "No active loans found for your account.";
    }

    public static String loanClientNotFound(final String languageCode) {
        return "rw".equalsIgnoreCase(languageCode) ? "Konti y'umukiriya ntiyabonetse." : "Client account could not be resolved.";
    }

    public static String loanBalanceResponse(final String languageCode, final String loanAccountNo, final String currency,
            final BigDecimal balance) {
        final String amount = balance != null ? balance.toPlainString() : "0";
        return "rw".equalsIgnoreCase(languageCode)
                ? "Inguzanyo " + loanAccountNo + ": " + amount + " " + currency + " asigaye."
                : "Loan " + loanAccountNo + " outstanding balance: " + amount + " " + currency;
    }

    public static String nextRepaymentResponse(final String languageCode, final String loanAccountNo, final LocalDate dueDate) {
        return "rw".equalsIgnoreCase(languageCode) ? "Inguzanyo " + loanAccountNo + ": itariki yo kwishyura ikurikira ni " + dueDate + "."
                : "Loan " + loanAccountNo + ": next repayment date is " + dueDate + ".";
    }

    public static String noUpcomingRepayment(final String languageCode, final String loanAccountNo) {
        return "rw".equalsIgnoreCase(languageCode) ? "Inguzanyo " + loanAccountNo + ": nta kwishyura kuzaza."
                : "Loan " + loanAccountNo + ": no upcoming repayment scheduled.";
    }

    public static String amountDueResponse(final String languageCode, final String loanAccountNo, final String currency,
            final BigDecimal amountDue, final LocalDate dueDate) {
        final String amount = amountDue != null ? amountDue.toPlainString() : "0";
        return "rw".equalsIgnoreCase(languageCode)
                ? "Inguzanyo " + loanAccountNo + ": " + amount + " " + currency + " kwishyura ku " + dueDate + "."
                : "Loan " + loanAccountNo + ": amount due " + amount + " " + currency + " on " + dueDate + ".";
    }

    public static String noAmountDue(final String languageCode, final String loanAccountNo) {
        return "rw".equalsIgnoreCase(languageCode) ? "Inguzanyo " + loanAccountNo + ": nta amafaranga asigaye kwishyura."
                : "Loan " + loanAccountNo + ": no amount currently due.";
    }

    public static String loanStatusResponse(final String languageCode, final String loanAccountNo, final String statusCode) {
        return "rw".equalsIgnoreCase(languageCode) ? "Inguzanyo " + loanAccountNo + ": imiterere ni " + statusCode + "."
                : "Loan " + loanAccountNo + " status: " + statusCode + ".";
    }
}
