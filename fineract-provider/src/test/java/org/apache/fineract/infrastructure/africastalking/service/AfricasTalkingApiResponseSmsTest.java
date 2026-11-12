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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.fineract.infrastructure.africastalking.service.AfricasTalkingClient.AfricasTalkingApiResponse;
import org.junit.jupiter.api.Test;

class AfricasTalkingApiResponseSmsTest {

    @Test
    void smsDeliverySuccessfulWhenRecipientStatusSuccess() {
        final String body = "{\"SMSMessageData\":{\"Recipients\":[{\"statusCode\":100,\"status\":\"Success\",\"number\":\"+254700\"}]}}";
        assertTrue(new AfricasTalkingApiResponse(201, body).isSmsDeliverySuccessful());
    }

    @Test
    void smsDeliveryFailsWhenRecipientRejectedDespiteHttp201() {
        final String body = "{\"SMSMessageData\":{\"Recipients\":[{\"statusCode\":403,\"status\":\"UserInBlacklist\",\"number\":\"+254700\"}]}}";
        assertFalse(new AfricasTalkingApiResponse(201, body).isSmsDeliverySuccessful());
    }

    @Test
    void smsDeliveryFailsOnNon2xx() {
        assertFalse(new AfricasTalkingApiResponse(500, "{\"SMSMessageData\":{\"Recipients\":[]}}").isSmsDeliverySuccessful());
    }
}
