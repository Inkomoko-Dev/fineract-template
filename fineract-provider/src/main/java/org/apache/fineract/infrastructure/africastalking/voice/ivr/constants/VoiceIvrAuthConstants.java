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
package org.apache.fineract.infrastructure.africastalking.voice.ivr.constants;

/**
 * Voice IVR authentication constraints.
 * <p>
 * OTP delivery is intentionally WhatsApp-only for CGLT-678. SMS/voice OTP fallbacks are out of scope until a separate
 * channel policy is approved.
 */
public final class VoiceIvrAuthConstants {

    public static final String OTP_DELIVERY_CHANNEL = "WHATSAPP";

    public static final String PENDING_RECORDING_CONSENT = "VOICE_RECORDING_CONSENT";

    private VoiceIvrAuthConstants() {}
}
