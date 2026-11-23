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
package org.apache.fineract.portfolio.paymentdetail.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.google.gson.JsonParser;
import java.util.HashMap;
import java.util.Map;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.portfolio.paymenttype.domain.PaymentType;
import org.junit.jupiter.api.Test;

public class PaymentDetailMfiCodeTest {

    private JsonCommand command(final String json) {
        return JsonCommand.from(json, JsonParser.parseString(json), new FromJsonHelper(), null, null, null, null, null, null, null, null,
                null, null, null, null);
    }

    @Test
    public void generatePaymentDetailTrimsSurroundingWhitespaceFromMfiCode() {
        final PaymentType paymentType = PaymentType.create("Cash", "Cash", true, false, 1L);
        final Map<String, Object> changes = new HashMap<>();
        final PaymentDetail detail = PaymentDetail.generatePaymentDetail(paymentType,
                command("{\"paymentTypeId\":1,\"mfiCode\":\"  MFI-001  \"}"), changes);

        assertEquals("MFI-001", detail.getMfiCode());
        assertEquals("MFI-001", changes.get("mfiCode"));
    }

    @Test
    public void generatePaymentDetailPreservesInternalSpacesInMfiCode() {
        final PaymentType paymentType = PaymentType.create("Cash", "Cash", true, false, 1L);
        final Map<String, Object> changes = new HashMap<>();
        final PaymentDetail detail = PaymentDetail.generatePaymentDetail(paymentType,
                command("{\"paymentTypeId\":1,\"mfiCode\":\" Express Union MFI \"}"), changes);

        assertEquals("Express Union MFI", detail.getMfiCode());
        assertEquals("Express Union MFI", changes.get("mfiCode"));
    }

    @Test
    public void generatePaymentDetailStoresNullWhenMfiCodeIsBlankAfterTrim() {
        final PaymentType paymentType = PaymentType.create("Cash", "Cash", true, false, 1L);
        final Map<String, Object> changes = new HashMap<>();
        final PaymentDetail detail = PaymentDetail.generatePaymentDetail(paymentType,
                command("{\"paymentTypeId\":1,\"mfiCode\":\"   \"}"), changes);

        assertNull(detail.getMfiCode());
    }
}
