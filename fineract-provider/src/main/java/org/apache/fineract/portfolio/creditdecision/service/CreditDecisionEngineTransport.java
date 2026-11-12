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
package org.apache.fineract.portfolio.creditdecision.service;

import java.io.IOException;

/** Delivers one request to the decision engine. Kept apart so the sending rules can be tested without a network. */
public interface CreditDecisionEngineTransport {

    /** One HTTP response. */
    final class Response {

        private final int status;
        private final String body;

        public Response(final int status, final String body) {
            this.status = status;
            this.body = body;
        }

        public int getStatus() {
            return status;
        }

        public String getBody() {
            return body;
        }
    }

    /**
     * @throws IOException
     *             when the engine cannot be reached or does not answer within the timeout
     */
    Response post(String url, String jsonBody, int timeoutSeconds, String bearerToken) throws IOException;
}
