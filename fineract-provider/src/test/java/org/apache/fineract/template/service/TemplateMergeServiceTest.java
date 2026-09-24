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
package org.apache.fineract.template.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.fineract.infrastructure.core.config.FineractProperties;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.exception.AbstractPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.template.domain.Template;
import org.apache.fineract.template.domain.TemplateEntity;
import org.apache.fineract.template.domain.TemplateMapper;
import org.apache.fineract.template.domain.TemplateType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.web.ServerProperties;

public class TemplateMergeServiceTest {

    private static final String AUTHORIZATION = "Basic aW5rb21va29EZXZzOnBhc3N3b3Jk";
    private static final String LOAN_MAPPER = "loans/{{loanId}}?associations=all&tenantIdentifier=default";
    private static final String LOAN_JSON = "{\"id\":7,\"accountNo\":\"000000007\",\"loanProductName\":\"Business Loan\","
            + "\"approvedPrincipal\":50000.0,\"closedOnDate\":null,\"timeline\":{\"actualDisbursementDate\":[2026,9,1]},"
            + "\"repaymentSchedule\":{\"periods\":[{\"period\":1,\"dueDate\":[2026,10,1]}]},\"multiDisburseTranches\":[1,2,3]}";

    private HttpServer server;
    private final List<Map<String, String>> requests = new ArrayList<>();
    private FineractProperties.FineractTemplateProperties templateProperties;
    private TemplateMergeService service;

    @BeforeEach
    public void setUp() throws IOException {
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        this.server.createContext("/fineract-provider/api/v1/loans/", exchange -> {
            final Map<String, String> request = new HashMap<>();
            request.put("uri", exchange.getRequestURI().toString());
            request.put("authorization", exchange.getRequestHeaders().getFirst("Authorization"));
            request.put("tenant", exchange.getRequestHeaders().getFirst("Fineract-Platform-TenantId"));
            this.requests.add(request);
            final boolean found = exchange.getRequestURI().getPath().endsWith("/loans/7");
            final byte[] body = (found ? LOAN_JSON : "{\"developerMessage\":\"not found\"}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(found ? 200 : 404, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        this.server.start();

        this.templateProperties = new FineractProperties.FineractTemplateProperties();
        this.templateProperties.setRegexWhitelistEnabled(true);
        this.templateProperties.setRegexWhitelist(new ArrayList<>());
        final FineractProperties fineractProperties = new FineractProperties();
        fineractProperties.setTemplate(this.templateProperties);

        final ServerProperties serverProperties = new ServerProperties();
        serverProperties.setAddress(this.server.getAddress().getAddress());
        serverProperties.setPort(this.server.getAddress().getPort());
        serverProperties.getServlet().setContextPath("/fineract-provider");

        this.service = new TemplateMergeService(fineractProperties, serverProperties);
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Africa/Nairobi", null));
    }

    @AfterEach
    public void tearDown() {
        this.server.stop(0);
        ThreadLocalContextUtil.clearTenant();
    }

    private static Template loanTemplate(final String text, final String mapperValue) {
        final List<TemplateMapper> mappers = new ArrayList<>();
        mappers.add(new TemplateMapper(0, "loan", mapperValue));
        return new Template("Contract test", text, TemplateEntity.LOAN, TemplateType.DOCUMENT, mappers);
    }

    private static Map<String, Object> loanScopes(final String loanId) {
        final Map<String, Object> scopes = new HashMap<>();
        scopes.put("loanId", loanId);
        scopes.put("BASE_URI", "https://cbs.example.org:1063/fineract-provider/api/v1/");
        return scopes;
    }

    private String merge(final Template template, final String loanId) throws IOException {
        return this.service.compile(template, loanScopes(loanId), AUTHORIZATION);
    }

    @Test
    public void staticTextWithTheDefaultLoanMapperRendersDespiteAnEmptyWhitelist() throws IOException {
        assertEquals("TEST CONTRACT - This is a test document.",
                merge(loanTemplate("TEST CONTRACT - This is a test document.", LOAN_MAPPER), "7"));
    }

    @Test
    public void loanVariablesResolveAgainstTheSelectedLoan() throws IOException {
        final String text = "Loan {{loan.accountNo}} / {{loan.loanProductName}} / {{loan.approvedPrincipal}}";
        assertEquals("Loan 000000007 / Business Loan / 50000.0", merge(loanTemplate(text, LOAN_MAPPER), "7"));
    }

    @Test
    public void relativeMapperIsFetchedFromThisServerNotFromTheRequestHost() throws IOException {
        merge(loanTemplate("x", LOAN_MAPPER), "7");
        assertEquals(1, this.requests.size());
        assertEquals("/fineract-provider/api/v1/loans/7?associations=all&tenantIdentifier=default", this.requests.get(0).get("uri"));
    }

    @Test
    public void relativeMapperForwardsTheCallersAuthorizationAndTenant() throws IOException {
        merge(loanTemplate("x", LOAN_MAPPER), "7");
        assertEquals(AUTHORIZATION, this.requests.get(0).get("authorization"));
        assertEquals("default", this.requests.get(0).get("tenant"));
    }

    @Test
    public void nullAndUnknownLoanFieldsRenderBlank() throws IOException {
        final String text = "[{{loan.closedOnDate}}][{{loan.accountNumber}}][{{loan.loanProduct.productName}}]";
        assertEquals("[][][]", merge(loanTemplate(text, LOAN_MAPPER), "7"));
    }

    @Test
    public void loanDatesRenderAsReadableDates() throws IOException {
        final String text = "{{loan.timeline.actualDisbursementDate}} / "
                + "{{#loan.repaymentSchedule.periods}}{{dueDate}}{{/loan.repaymentSchedule.periods}}";
        assertEquals("01 September 2026 / 01 October 2026", merge(loanTemplate(text, LOAN_MAPPER), "7"));
    }

    @Test
    public void numericListsThatAreNotDatesAreLeftAlone() throws IOException {
        assertEquals("123", merge(loanTemplate("{{#loan.multiDisburseTranches}}{{.}}{{/loan.multiDisburseTranches}}", LOAN_MAPPER), "7"));
    }

    @Test
    public void absoluteMapperOutsideTheWhitelistIsADomainRuleViolation() {
        final AbstractPlatformDomainRuleException exception = assertThrows(AbstractPlatformDomainRuleException.class,
                () -> merge(loanTemplate("x", "https://attacker.example/steal"), "7"));
        assertEquals("error.msg.template.url.forbidden", exception.getGlobalisationMessageCode());
        assertTrue(this.requests.isEmpty());
    }

    @Test
    public void absoluteMapperMatchingTheWhitelistIsFetchedWithTheCallersAuthorization() throws IOException {
        final String base = "http://127.0.0.1:" + this.server.getAddress().getPort();
        this.templateProperties.getRegexWhitelist().add("http://127\\.0\\.0\\.1:\\d+/.*");
        assertEquals("000000007", merge(loanTemplate("{{loan.accountNo}}", base + "/fineract-provider/api/v1/loans/{{loanId}}"), "7"));
        assertEquals(AUTHORIZATION, this.requests.get(0).get("authorization"));
    }

    @Test
    public void mapperThatCannotBeLoadedIsADomainRuleViolationNamingTheMapper() {
        final AbstractPlatformDomainRuleException exception = assertThrows(AbstractPlatformDomainRuleException.class,
                () -> merge(loanTemplate("{{loan.accountNo}}", LOAN_MAPPER), "999"));
        assertEquals("error.msg.template.mapper.fetch.failed", exception.getGlobalisationMessageCode());
        assertTrue(exception.getDefaultUserMessage().contains("`loan`"));
        assertTrue(exception.getDefaultUserMessage().contains("HTTP 404"));
    }

    @Test
    public void invalidTemplateSyntaxIsAValidationErrorOnText() {
        final PlatformApiDataValidationException exception = assertThrows(PlatformApiDataValidationException.class,
                () -> merge(loanTemplate("{{#loan}}unclosed section", LOAN_MAPPER), "7"));
        assertEquals("text", exception.getErrors().get(0).getParameterName());
    }

    @Test
    public void callerScopesAreNotMutated() throws IOException {
        final Map<String, Object> scopes = loanScopes("7");
        this.service.compile(loanTemplate("{{loan.accountNo}}", LOAN_MAPPER), scopes, AUTHORIZATION);
        assertEquals(loanScopes("7"), scopes);
    }
}
