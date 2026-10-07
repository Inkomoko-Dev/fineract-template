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
package org.apache.fineract.portfolio.loanaccount.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import org.apache.commons.lang3.StringUtils;

/**
 * Body returned by Payment Hub {@code POST /api/v1/transactions/query-status}.
 */
public final class PaymentHubTransactionStatus {

    public static final String PENDING = "PENDING";
    public static final String SUCCESS = "SUCCESS";
    public static final String FAILED = "FAILED";
    public static final String PENDING_ERROR = "PENDING_ERROR";

    private final String requestId;
    private final String status;
    private final String transactionRef;
    private final String reason;
    private final String transactionId;
    private final Integer statusCode;
    private final String transactionType;
    private final BigDecimal amount;
    private final String currency;
    private final String externalId;
    private final String transactionDate;

    private PaymentHubTransactionStatus(final String requestId, final String status, final String transactionRef, final String reason,
            final String transactionId, final Integer statusCode, final String transactionType, final BigDecimal amount,
            final String currency, final String externalId, final String transactionDate) {
        this.requestId = requestId;
        this.status = status;
        this.transactionRef = transactionRef;
        this.reason = reason;
        this.transactionId = transactionId;
        this.statusCode = statusCode;
        this.transactionType = transactionType;
        this.amount = amount;
        this.currency = currency;
        this.externalId = externalId;
        this.transactionDate = transactionDate;
    }

    public static PaymentHubTransactionStatus parse(final String responseBody) {
        if (StringUtils.isBlank(responseBody)) {
            return null;
        }
        try {
            final JsonElement element = JsonParser.parseString(responseBody);
            if (!element.isJsonObject()) {
                return null;
            }
            final JsonObject body = element.getAsJsonObject();
            if (!body.has("status") || !body.get("status").isJsonPrimitive() || !body.get("status").getAsJsonPrimitive().isString()) {
                return null;
            }
            return new PaymentHubTransactionStatus(jsonString(body, "requestId"), jsonString(body, "status"),
                    jsonString(body, "transactionRef"), jsonString(body, "reason"), jsonString(body, "transactionId"),
                    jsonInteger(body, "statusCode"), jsonString(body, "transactionType"), jsonDecimal(body, "amount"),
                    jsonString(body, "currency"), jsonString(body, "externalId"), jsonString(body, "transactionDate"));
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static String queryRequestJson(final String requestId, final String transactionId) {
        final JsonObject body = new JsonObject();
        if (StringUtils.isNotBlank(requestId)) {
            body.addProperty("requestId", requestId);
        }
        if (StringUtils.isNotBlank(transactionId)) {
            body.addProperty("transactionId", transactionId);
        }
        return body.toString();
    }

    public static LocalDate parseTransactionDate(final String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).toLocalDate();
        } catch (DateTimeParseException ignored) {
            // try the next ISO-8601 shape
        }
        try {
            return LocalDateTime.parse(value).toLocalDate();
        } catch (DateTimeParseException ignored) {
            // try a date-only value
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    public boolean isPending() {
        return PENDING.equals(this.status);
    }

    public boolean isSuccess() {
        return SUCCESS.equals(this.status);
    }

    public boolean isFailed() {
        return FAILED.equals(this.status);
    }

    public boolean isPendingError() {
        return PENDING_ERROR.equals(this.status);
    }

    public boolean isTerminal() {
        return isSuccess() || isFailed() || isPendingError();
    }

    public String getRequestId() {
        return this.requestId;
    }

    public String getStatus() {
        return this.status;
    }

    public String getTransactionRef() {
        return this.transactionRef;
    }

    public String getReason() {
        return this.reason;
    }

    public String getTransactionId() {
        return this.transactionId;
    }

    public Integer getStatusCode() {
        return this.statusCode;
    }

    public String getTransactionType() {
        return this.transactionType;
    }

    public BigDecimal getAmount() {
        return this.amount;
    }

    public String getCurrency() {
        return this.currency;
    }

    public String getExternalId() {
        return this.externalId;
    }

    public String getTransactionDate() {
        return this.transactionDate;
    }

    private static String jsonString(final JsonObject object, final String property) {
        if (object == null || !object.has(property) || object.get(property).isJsonNull()) {
            return null;
        }
        if (!object.get(property).isJsonPrimitive()) {
            return null;
        }
        return object.get(property).getAsString();
    }

    private static Integer jsonInteger(final JsonObject object, final String property) {
        if (object == null || !object.has(property) || object.get(property).isJsonNull() || !object.get(property).isJsonPrimitive()) {
            return null;
        }
        try {
            return object.get(property).getAsInt();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static BigDecimal jsonDecimal(final JsonObject object, final String property) {
        if (object == null || !object.has(property) || object.get(property).isJsonNull() || !object.get(property).isJsonPrimitive()) {
            return null;
        }
        try {
            return object.get(property).getAsBigDecimal();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
