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
package org.apache.fineract.organisation.externalmapping.data;

import java.util.ArrayList;
import java.util.List;

public class ExternalOrganizationMappingImportResult {

    private int created;
    private int updated;
    private int skipped;
    private final List<String> errors = new ArrayList<>();

    public void incrementCreated() {
        created++;
    }

    public void incrementUpdated() {
        updated++;
    }

    public void incrementSkipped() {
        skipped++;
    }

    public void addError(final String error) {
        errors.add(error);
    }

    public int getCreated() {
        return created;
    }

    public int getUpdated() {
        return updated;
    }

    public int getSkipped() {
        return skipped;
    }

    public List<String> getErrors() {
        return errors;
    }
}
