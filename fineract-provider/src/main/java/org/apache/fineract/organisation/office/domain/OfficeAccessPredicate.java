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
package org.apache.fineract.organisation.office.domain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class OfficeAccessPredicate {

    private final String sql;
    private final List<Object> parameters;

    OfficeAccessPredicate(final String sql, final List<Object> parameters) {
        this.sql = sql;
        this.parameters = parameters;
    }

    public String getSql() {
        return this.sql;
    }

    public List<Object> getParameters() {
        return Collections.unmodifiableList(this.parameters);
    }

    public Object[] getArguments() {
        return this.parameters.toArray();
    }

    public String rewrite(final String sql, final String stockPredicate) {
        if (sql == null || !sql.contains(stockPredicate)) {
            throw new IllegalStateException("Office restriction '" + stockPredicate + "' is not present in the statement to be scoped");
        }
        return sql.replace(stockPredicate, this.sql);
    }

    public Object[] argumentsPrecededBy(final Object... leading) {
        final List<Object> arguments = new ArrayList<>();
        if (leading != null) {
            arguments.addAll(Arrays.asList(leading));
        }
        arguments.addAll(this.parameters);
        return arguments.toArray();
    }

    public Object[] argumentsFollowedBy(final Object... trailing) {
        final List<Object> arguments = new ArrayList<>(this.parameters);
        if (trailing != null) {
            arguments.addAll(Arrays.asList(trailing));
        }
        return arguments.toArray();
    }

    @Override
    public String toString() {
        return this.sql + " " + this.parameters;
    }
}
