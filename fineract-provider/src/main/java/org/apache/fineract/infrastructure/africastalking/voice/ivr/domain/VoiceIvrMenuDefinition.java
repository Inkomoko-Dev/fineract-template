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
package org.apache.fineract.infrastructure.africastalking.voice.ivr.domain;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;

@Entity
@Table(name = "voice_ivr_menu_definition")
@Getter
@Setter
@NoArgsConstructor
public class VoiceIvrMenuDefinition extends AbstractPersistableCustom {

    @Column(name = "menu_key", length = 100, nullable = false)
    private String menuKey;

    @Column(name = "language_code", length = 15, nullable = false)
    private String languageCode;

    @Column(name = "prompt_text", length = 500, nullable = false)
    private String promptText;

    @Column(name = "parent_menu_key", length = 100)
    private String parentMenuKey;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;
}
