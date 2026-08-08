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
package org.apache.fineract.infrastructure.campaigns.whatsapp.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class WhatsAppTemplateVariableMapper {

    private WhatsAppTemplateVariableMapper() {}

    public static List<String> toBodyValues(String mappingJson, Map<String, Object> row) {
        return toBodyValuesStrict(mappingJson, row).getBodyValues();
    }

    public static MappingResult toBodyValuesStrict(String mappingJson, Map<String, Object> row) {
        if (mappingJson == null || mappingJson.isBlank()) {
            return new MappingResult(List.of(), List.of());
        }
        JsonArray arr = JsonParser.parseString(mappingJson).getAsJsonArray();
        List<String> out = new ArrayList<>();
        List<String> unresolved = new ArrayList<>();
        for (JsonElement el : arr) {
            String key = el.getAsString();
            Object v = row == null ? null : row.get(key);
            String value = v == null ? "" : String.valueOf(v);
            if (value.isBlank()) {
                unresolved.add(key);
            }
            out.add(value);
        }
        return new MappingResult(out, unresolved);
    }

    public static final class MappingResult {

        private final List<String> bodyValues;
        private final List<String> unresolvedKeys;

        private MappingResult(final List<String> bodyValues, final List<String> unresolvedKeys) {
            this.bodyValues = bodyValues;
            this.unresolvedKeys = unresolvedKeys;
        }

        public List<String> getBodyValues() {
            return this.bodyValues;
        }

        public List<String> getUnresolvedKeys() {
            return this.unresolvedKeys;
        }

        public boolean isComplete() {
            return this.unresolvedKeys.isEmpty();
        }
    }
}
