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

package org.apache.fineract.infrastructure.core.config;

import java.io.IOException;
import javax.servlet.ServletException;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ValveBase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnClass(name = "org.apache.coyote.http11.Http11Nio2Protocol")
public class TomcatConnectorCustomizer implements WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> {

    public static final String TCP_PEER_ADDRESS_ATTRIBUTE = TomcatConnectorCustomizer.class.getName() + ".tcpPeerAddress";

    @Override
    public void customize(ConfigurableServletWebServerFactory factory) {
        final var tomcat = (TomcatServletWebServerFactory) factory;
        tomcat.setProtocol("org.apache.coyote.http11.Http11Nio2Protocol");
        tomcat.addEngineValves(new ValveBase(true) {

            @Override
            public void invoke(final Request request, final Response response) throws IOException, ServletException {
                // Capture the connection address before servlet filters can apply forwarded headers.
                request.setAttribute(TCP_PEER_ADDRESS_ATTRIBUTE, request.getPeerAddr());
                getNext().invoke(request, response);
            }
        });
    }
}
