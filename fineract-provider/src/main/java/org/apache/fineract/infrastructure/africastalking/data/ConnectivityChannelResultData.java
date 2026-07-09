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
package org.apache.fineract.infrastructure.africastalking.data;

import lombok.Getter;

@Getter
public class ConnectivityChannelResultData {

    private final String channel;
    private final boolean connected;
    private final Long latencyMillis;
    private final String message;

    public ConnectivityChannelResultData(final String channel, final boolean connected, final Long latencyMillis, final String message) {
        this.channel = channel;
        this.connected = connected;
        this.latencyMillis = latencyMillis;
        this.message = message;
    }
}
