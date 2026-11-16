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
package org.apache.fineract.commands.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.gson.JsonParser;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.fineract.commands.annotation.CommandType;
import org.apache.fineract.commands.domain.CommandSourceRepository;
import org.apache.fineract.commands.domain.CommandWrapper;
import org.apache.fineract.commands.handler.NewCommandSourceHandler;
import org.apache.fineract.commands.provider.CommandHandlerProvider;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.serialization.ToApiJsonSerializer;
import org.apache.fineract.infrastructure.ingestion.LogReporter;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.handler.ApproveBulkRescheduleCommandHandler;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.handler.CreateBulkRescheduleCommandHandler;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.handler.RejectBulkRescheduleCommandHandler;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.handler.UndoBulkRescheduleCommandHandler;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.service.BulkReschedulePreviewService;
import org.apache.fineract.portfolio.loanaccount.rescheduleloan.RescheduleLoansApiConstants;
import org.apache.fineract.portfolio.loanaccount.rescheduleloan.handler.ApproveLoanRescheduleRequestCommandHandler;
import org.apache.fineract.portfolio.loanaccount.rescheduleloan.handler.CreateLoanRescheduleRequestCommandHandler;
import org.apache.fineract.portfolio.loanaccount.rescheduleloan.handler.RejectLoanRescheduleRequestCommandHandler;
import org.apache.fineract.portfolio.loanaccount.rescheduleloan.handler.UndoLoanRescheduleCommandHandler;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * CGLT-824: individual loan reschedule CREATE must not be stolen by the bulk dry-run handler.
 */
@ExtendWith(MockitoExtension.class)
class RescheduleLoanCommandRoutingTest {

    private static final Set<String> COLLISION_ACTIONS = Set.of("CREATE", "APPROVE", "REJECT", "UNDO");

    private static final List<Class<?>> BULK_HANDLERS_THAT_MUST_NOT_OWN_INDIVIDUAL_ACTIONS = List.of(
            CreateBulkRescheduleCommandHandler.class, ApproveBulkRescheduleCommandHandler.class, RejectBulkRescheduleCommandHandler.class,
            UndoBulkRescheduleCommandHandler.class);

    @Mock
    private PlatformSecurityContext platformSecurityContext;
    @Mock
    private ApplicationContext applicationContext;
    @Mock
    private ToApiJsonSerializer<Map<String, Object>> toApiJsonSerializer;
    @Mock
    private ToApiJsonSerializer<CommandProcessingResult> toApiResultJsonSerializer;
    @Mock
    private CommandSourceRepository commandSourceRepository;
    @Mock
    private ConfigurationDomainService configurationDomainService;
    @Mock
    private CommandHandlerProvider commandHandlerProvider;
    @Mock
    private MakerCheckerNotificationService notificationService;
    @Mock
    private LogReporter logReporter;

    private SynchronousCommandProcessingService commandProcessingService;
    private final FromJsonHelper fromJsonHelper = new FromJsonHelper();

    @BeforeEach
    void setUp() {
        commandProcessingService = new SynchronousCommandProcessingService(platformSecurityContext, applicationContext, toApiJsonSerializer,
                toApiResultJsonSerializer, commandSourceRepository, configurationDomainService, commandHandlerProvider, notificationService,
                logReporter);
    }

    @Test
    @DisplayName("individual CREATE href routes to CommandHandlerProvider, not bulk create bean")
    void individualCreateUsesProviderNotBulkBean() {
        final CommandWrapper wrapper = new CommandWrapperBuilder().createLoanRescheduleRequest(RescheduleLoansApiConstants.ENTITY_NAME)
                .withJson("{}").build();
        final NewCommandSourceHandler individualHandler = mock(NewCommandSourceHandler.class);
        when(commandHandlerProvider.getHandler("RESCHEDULELOAN", "CREATE")).thenReturn(individualHandler);

        final NewCommandSourceHandler resolved = findCommandHandler(wrapper);

        assertThat(wrapper.isBulkRescheduleResource()).isFalse();
        assertThat(wrapper.getHref()).isEqualTo("/rescheduleloans");
        assertThat(resolved).isSameAs(individualHandler);
        verify(commandHandlerProvider).getHandler("RESCHEDULELOAN", "CREATE");
        verifyNoInteractions(applicationContext);
    }

    @Test
    @DisplayName("bulk CREATE href resolves createBulkRescheduleCommandHandler bean")
    void bulkCreateUsesBulkHandlerBean() {
        final CommandWrapper wrapper = new CommandWrapperBuilder().createBulkReschedule().withJson("{}").build();
        final NewCommandSourceHandler bulkHandler = mock(NewCommandSourceHandler.class);
        when(applicationContext.getBean("createBulkRescheduleCommandHandler", NewCommandSourceHandler.class)).thenReturn(bulkHandler);

        final NewCommandSourceHandler resolved = findCommandHandler(wrapper);

        assertThat(wrapper.isBulkRescheduleResource()).isTrue();
        assertThat(wrapper.getHref()).isEqualTo("/bulk-reschedule");
        assertThat(resolved).isSameAs(bulkHandler);
        verify(applicationContext).getBean("createBulkRescheduleCommandHandler", NewCommandSourceHandler.class);
        verifyNoInteractions(commandHandlerProvider);
    }

    @Test
    @DisplayName("bulk handlers must not re-register RESCHEDULELOAN CREATE/APPROVE/REJECT/UNDO CommandType")
    void bulkHandlersDoNotStealIndividualCommandTypes() {
        for (final Class<?> handlerClass : BULK_HANDLERS_THAT_MUST_NOT_OWN_INDIVIDUAL_ACTIONS) {
            final CommandType commandType = handlerClass.getAnnotation(CommandType.class);
            assertThat(commandType).as("%s must not carry @CommandType for individual collision actions", handlerClass.getSimpleName())
                    .isNull();
        }
    }

    @Test
    @DisplayName("individual handlers own RESCHEDULELOAN CREATE/APPROVE/REJECT/UNDO")
    void individualHandlersOwnSharedActions() {
        assertCommandType(CreateLoanRescheduleRequestCommandHandler.class, "CREATE");
        assertCommandType(ApproveLoanRescheduleRequestCommandHandler.class, "APPROVE");
        assertCommandType(RejectLoanRescheduleRequestCommandHandler.class, "REJECT");
        assertCommandType(UndoLoanRescheduleCommandHandler.class, "UNDO");
    }

    @Test
    @DisplayName("individual-style payload without filters fails bulk dry-run with the known filters error")
    void individualPayloadOnBulkDryRunRequiresFilters() {
        final BulkReschedulePreviewService previewService = new BulkReschedulePreviewService(platformSecurityContext, null, null, null, null,
                null, null);
        final AppUser user = mock(AppUser.class);
        when(platformSecurityContext.authenticatedUser()).thenReturn(user);

        final String individualJson = "{\"loanId\":430024,\"rescheduleReasonId\":1,\"submittedOnDate\":\"16 November 2026\","
                + "\"rescheduleFromDate\":\"16 November 2026\",\"repaymentEvery\":1,\"repaymentFrequencyType\":2,"
                + "\"dateFormat\":\"dd MMMM yyyy\",\"locale\":\"en\"}";
        final JsonCommand command = JsonCommand.from(individualJson, JsonParser.parseString(individualJson), fromJsonHelper,
                "RESCHEDULELOAN", null, null, null, null, 430024L, null, null, "/rescheduleloans", null, null, null);

        assertThatThrownBy(() -> previewService.performDryRun(command)).isInstanceOf(GeneralPlatformDomainRuleException.class)
                .hasMessageContaining("Bulk reschedule request and filters are required");
        verify(user).validateHasPermissionTo("CREATE_RESCHEDULELOAN");
    }

    private NewCommandSourceHandler findCommandHandler(final CommandWrapper wrapper) {
        return ReflectionTestUtils.invokeMethod(commandProcessingService, "findCommandHandler", wrapper);
    }

    private static void assertCommandType(final Class<?> handlerClass, final String action) {
        final CommandType commandType = handlerClass.getAnnotation(CommandType.class);
        assertThat(commandType).as("%s must be annotated @CommandType", handlerClass.getSimpleName()).isNotNull();
        assertThat(commandType.entity()).isEqualTo("RESCHEDULELOAN");
        assertThat(commandType.action()).isEqualTo(action);
        assertThat(COLLISION_ACTIONS).contains(action);
    }
}
