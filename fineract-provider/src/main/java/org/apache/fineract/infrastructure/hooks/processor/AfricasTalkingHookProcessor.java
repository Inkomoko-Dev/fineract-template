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
package org.apache.fineract.infrastructure.hooks.processor;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.domain.FineractContext;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.hooks.domain.Hook;
import org.apache.fineract.infrastructure.africastalking.service.AfricasTalkingWhatsAppService;
import org.apache.fineract.organisation.staff.domain.Staff;
import org.apache.fineract.organisation.staff.domain.StaffRepositoryWrapper;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.client.domain.ClientRepositoryWrapper;
import org.apache.fineract.template.domain.Template;
import org.apache.fineract.template.domain.TemplateRepository;
import org.apache.fineract.template.service.TemplateMergeService;
import org.springframework.stereotype.Service;

@Service("africasTalkingHookProcessor")
@Slf4j
@RequiredArgsConstructor
public class AfricasTalkingHookProcessor implements HookProcessor {

    private final ClientRepositoryWrapper clientRepository;
    private final StaffRepositoryWrapper staffRepository;
    private final TemplateRepository templateRepository;
    private final TemplateMergeService templateMergeService;
    private final AfricasTalkingWhatsAppService africasTalkingWhatsAppService;

    @Override
    public void process(final Hook hook, final String payload, final String entityName, final String actionName,
            final FineractContext context) throws IOException {
        final String templateName = entityName + "_" + actionName;
        Template template;
        final List<Template> templates = this.templateRepository.findByTemplateMapper("SMS_template_Key", templateName);
        if (templates.isEmpty()) {
            template = hook.getUgdTemplate();
        } else {
            template = templates.get(0);
        }
        if (template == null) {
            log.error("Template not found for WhatsApp hook {}", templateName);
            throw new GeneralPlatformDomainRuleException("error.msg.templates.not.found", "Template not found", templateName);
        }

        final Type type = new TypeToken<Map<String, Object>>() {}.getType();
        final Map<String, Object> reqMap = new Gson().fromJson(payload, type);
        if (reqMap.get("clientId") != null) {
            final Long clientId = ((Number) reqMap.get("clientId")).longValue();
            final Client client = clientRepository.findOneWithNotFoundDetection(clientId);
            reqMap.put("clientName", client.getDisplayName());
            final String messageText = this.templateMergeService.compile(template, reqMap);
            final String requestJson = String.format("{\"clientId\":%d,\"message\":%s,\"sendImmediately\":true}", clientId,
                    new Gson().toJson(messageText));
            africasTalkingWhatsAppService.queueOutboundMessage(requestJson);
            return;
        }
        if (reqMap.get("staffId") != null) {
            final Long staffId = ((Number) reqMap.get("staffId")).longValue();
            final Staff staff = staffRepository.findOneWithNotFoundDetection(staffId);
            reqMap.put("staffName", staff.displayName());
            final String messageText = this.templateMergeService.compile(template, reqMap);
            final String requestJson = String.format("{\"staffId\":%d,\"message\":%s,\"sendImmediately\":true}", staffId,
                    new Gson().toJson(messageText));
            africasTalkingWhatsAppService.queueOutboundMessage(requestJson);
        }
    }
}
