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
package org.apache.fineract.infrastructure.whatsapp.provider;

public record WhatsAppTransportResult(WhatsAppProvider provider, boolean successful, int statusCode, String body, String externalId,
        String errorDetail) {

    public static WhatsAppTransportResult success(final WhatsAppProvider provider, final int statusCode, final String body,
            final String externalId) {
        return new WhatsAppTransportResult(provider, true, statusCode, body, externalId, null);
    }

    public static WhatsAppTransportResult failure(final WhatsAppProvider provider, final int statusCode, final String body,
            final String errorDetail) {
        return new WhatsAppTransportResult(provider, false, statusCode, body, null, errorDetail);
    }

    public static WhatsAppTransportResult exception(final WhatsAppProvider provider, final Exception exception) {
        final String detail = exception == null ? "unknown error" : exception.getMessage();
        return new WhatsAppTransportResult(provider, false, 0, null, null, detail);
    }
}
