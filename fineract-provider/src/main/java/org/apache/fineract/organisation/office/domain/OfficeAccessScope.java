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
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * The set of office hierarchies a user is allowed to read data from, together with whether that access reaches down into
 * child offices.
 */
public final class OfficeAccessScope {

    private static final Pattern OFFICE_HIERARCHY = Pattern.compile("^\\.(\\d+\\.)*$");

    private final List<String> hierarchies;
    private final boolean includeDescendants;

    private OfficeAccessScope(final List<String> hierarchies, final boolean includeDescendants) {
        this.hierarchies = hierarchies;
        this.includeDescendants = includeDescendants;
    }

    public static OfficeAccessScope hierarchical(final Collection<String> hierarchies) {
        return new OfficeAccessScope(collapse(validated(hierarchies)), true);
    }

    public static OfficeAccessScope exact(final Collection<String> hierarchies) {
        return new OfficeAccessScope(validated(hierarchies), false);
    }

    private static List<String> validated(final Collection<String> hierarchies) {
        if (hierarchies == null || hierarchies.isEmpty()) {
            throw new IllegalArgumentException("An office access scope needs at least one office hierarchy");
        }
        final TreeSet<String> sorted = new TreeSet<>();
        for (final String hierarchy : hierarchies) {
            if (hierarchy == null || !OFFICE_HIERARCHY.matcher(hierarchy).matches()) {
                throw new IllegalArgumentException("Not an office hierarchy: " + hierarchy);
            }
            sorted.add(hierarchy);
        }
        return new ArrayList<>(sorted);
    }

    private static List<String> collapse(final List<String> sortedHierarchies) {
        final List<String> collapsed = new ArrayList<>(sortedHierarchies.size());
        for (final String hierarchy : sortedHierarchies) {
            boolean coveredByAnAncestor = false;
            for (final String kept : collapsed) {
                if (hierarchy.startsWith(kept)) {
                    coveredByAnAncestor = true;
                    break;
                }
            }
            if (!coveredByAnAncestor) {
                collapsed.add(hierarchy);
            }
        }
        return collapsed;
    }

    public List<String> getHierarchies() {
        return Collections.unmodifiableList(this.hierarchies);
    }

    public boolean isIncludeDescendants() {
        return this.includeDescendants;
    }

    public String primaryHierarchy() {
        return this.hierarchies.get(0);
    }

    public boolean covers(final String officeHierarchy) {
        if (officeHierarchy == null) {
            return false;
        }
        for (final String hierarchy : this.hierarchies) {
            if (this.includeDescendants ? officeHierarchy.startsWith(hierarchy) : officeHierarchy.equals(hierarchy)) {
                return true;
            }
        }
        return false;
    }

    public String sqlPredicate(final String... columnExpressions) {
        if (columnExpressions == null || columnExpressions.length == 0) {
            throw new IllegalArgumentException("An office access predicate needs at least one column");
        }
        final StringBuilder predicate = new StringBuilder("(");
        for (final String column : columnExpressions) {
            for (final String hierarchy : this.hierarchies) {
                if (predicate.length() > 1) {
                    predicate.append(" or ");
                }
                predicate.append(column);
                if (this.includeDescendants) {
                    predicate.append(" like '").append(hierarchy).append("%'");
                } else {
                    predicate.append(" = '").append(hierarchy).append("'");
                }
            }
        }
        return predicate.append(")").toString();
    }

    @Override
    public String toString() {
        return "OfficeAccessScope" + this.hierarchies + (this.includeDescendants ? "+descendants" : "");
    }
}
