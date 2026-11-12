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
package org.apache.fineract.infrastructure.africastalking.voice.ivr.service;

public final class VoiceIvrMessages {

    private VoiceIvrMessages() {}

    public static String clientWelcome(final String languageCode, final String displayName) {
        return "rw".equalsIgnoreCase(languageCode) ? "Murakaza neza, " + displayName + "."
                : "Welcome back, " + displayName + ".";
    }

    public static String staffWelcome(final String languageCode, final String displayName) {
        return "rw".equalsIgnoreCase(languageCode) ? "Murakaza neza, " + displayName + "."
                : "Welcome, " + displayName + ".";
    }

    public static String confirmIdentityPrompt(final String languageCode, final String displayName) {
        return "rw".equalsIgnoreCase(languageCode) ? "Twemeza ko uri " + displayName + ". Kanda 1 kwemeza cyangwa andika nimero ya konti yawe ukurikije hash."
                : "We found your profile as " + displayName + ". Press 1 to confirm or enter your client account number followed by hash.";
    }

    public static String enterClientAccountPrompt(final String languageCode) {
        return "rw".equalsIgnoreCase(languageCode) ? "Andika nimero ya konti yawe ya Inkomoko ukurikije hash."
                : "Enter your Inkomoko client account number followed by hash.";
    }

    public static String identityNotFound(final String languageCode) {
        return "rw".equalsIgnoreCase(languageCode) ? "Konti ntiyabonetse. Ongera ugerageze."
                : "We could not verify your identity. Please try again.";
    }

    public static String otpSentPrompt(final String languageCode) {
        return "rw".equalsIgnoreCase(languageCode)
                ? "Twoherejwe kode y'igenzura kuri WhatsApp yawe. Andika kode ukurikije hash."
                : "We sent a verification code to your WhatsApp number. Enter the code followed by hash.";
    }

    public static String otpDeliveryFailed(final String languageCode) {
        return "rw".equalsIgnoreCase(languageCode)
                ? "Ntitwashoboye kohereza kode kuri WhatsApp yawe. Hamagara umujyanama wacu cyangwa gerageza nyuma."
                : "We could not deliver a verification code to your WhatsApp number. Please contact an advisor or try again later.";
    }

    public static String otpInvalid(final String languageCode) {
        return "rw".equalsIgnoreCase(languageCode) ? "Kode siyo. Ongera ugerageze."
                : "Invalid or expired verification code. Please try again.";
    }

    public static String authSuccess(final String languageCode) {
        return "rw".equalsIgnoreCase(languageCode) ? "Wemejwe neza." : "Authentication successful.";
    }

    public static String afterHoursMenu(final String languageCode) {
        return "rw".equalsIgnoreCase(languageCode)
                ? "Ofisi irafunze. Kanda 1 kugirango tugusubire hamagara, cyangwa 2 kugira ngo usige ubutumwa."
                : "Our office is currently closed. Press 1 for a callback, or 2 to leave a voicemail.";
    }

    public static String callbackConfirmed(final String languageCode, final String ticketNumber) {
        return "rw".equalsIgnoreCase(languageCode) ? "Twakiriye icyifuzo cyawe cyo gusubizwa. Nimero y'itike ni " + ticketNumber + "."
                : "Your callback request has been received. Reference number " + ticketNumber + ".";
    }

    public static String voicemailPrompt(final String languageCode) {
        return "rw".equalsIgnoreCase(languageCode) ? "Nyamuneka usige ubutumwa bwawe nyuma y'impuruza."
                : "Please leave your message after the tone.";
    }

    public static String voicemailThankYou(final String languageCode) {
        return "rw".equalsIgnoreCase(languageCode) ? "Murakoze. Tuzasubiza vuba bishoboka."
                : "Thank you. We will get back to you as soon as possible.";
    }

    public static String queuePosition(final String languageCode, final int position) {
        return "rw".equalsIgnoreCase(languageCode) ? "Uri umukiriya wa " + position + " mu murongo. Tegereza gato."
                : "You are caller number " + position + " in the queue. Please hold.";
    }

    public static String connectingToAgent(final String languageCode, final String departmentLabel) {
        return "rw".equalsIgnoreCase(languageCode) ? "Turimo kuguhuza na " + departmentLabel + "."
                : "Connecting you to " + departmentLabel + ".";
    }

    public static String recordingConsentPrompt(final String languageCode) {
        return "rw".equalsIgnoreCase(languageCode)
                ? "Iyi telefone irashobora gufatwa. Kanda 1 kwemera no gukomeza, cyangwa 2 gukomeza nta gufatwa."
                : "This call may be recorded for quality and training. Press 1 to consent and continue, or 2 to continue without recording.";
    }

    public static String recordingConsentInvalid(final String languageCode) {
        return "rw".equalsIgnoreCase(languageCode) ? "Hitamo 1 cyangwa 2."
                : "Please press 1 to consent or 2 to continue without recording.";
    }

    public static String goodbye(final String languageCode) {
        return "rw".equalsIgnoreCase(languageCode) ? "Murakoze guhamagara Inkomoko. Muraho."
                : "Thank you for calling Inkomoko. Goodbye.";
    }

    public static String sanitizeForSpeech(final String text) {
        if (text == null) {
            return "";
        }
        return text.replace('\n', ' ').trim();
    }
}
