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

/**
 * An office restriction rendered as SQL with bound placeholders, together with the values to bind for them.
 */
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

    /**
     * The values to bind when this predicate is the only placeholder-bearing part of the statement.
     */
    public Object[] getArguments() {
        return this.parameters.toArray();
    }

    /**
     * The values to bind when placeholders appear before this predicate in the statement.
     */
    public Object[] argumentsPrecededBy(final Object... leading) {
        final List<Object> arguments = new ArrayList<>();
        if (leading != null) {
            arguments.addAll(Arrays.asList(leading));
        }
        arguments.addAll(this.parameters);
        return arguments.toArray();
    }

    /**
     * The values to bind when placeholders appear after this predicate in the statement.
     */
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
