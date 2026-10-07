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
package org.apache.fineract.infrastructure.security.utils;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class SQLBuilderPredicateTest {

    @Test
    @DisplayName("A bound predicate is ANDed after the other criteria with its arguments in placeholder order")
    public void predicateIsAndedWithItsArguments() {
        final SQLBuilder builder = new SQLBuilder();
        builder.addCriteria("g.office_id =", 5L);
        builder.addPredicate("(o.hierarchy like ? or o.hierarchy like ?)", List.of(".1.2.%", ".1.3.%"));

        assertEquals(" WHERE  g.office_id = ?  AND  (o.hierarchy like ? or o.hierarchy like ?)", builder.getSQLTemplate());
        assertArrayEquals(new Object[] { 5L, ".1.2.%", ".1.3.%" }, builder.getArguments());
    }

    @Test
    @DisplayName("A bound predicate on its own becomes the whole WHERE clause")
    public void predicateAloneIsTheWhereClause() {
        final SQLBuilder builder = new SQLBuilder();
        builder.addPredicate("(o.hierarchy = ?)", List.of(".1.2."));

        assertEquals(" WHERE  (o.hierarchy = ?)", builder.getSQLTemplate());
        assertArrayEquals(new Object[] { ".1.2." }, builder.getArguments());
    }

    @Test
    @DisplayName("A predicate whose placeholders and arguments disagree is rejected")
    public void predicateWithMismatchedArgumentsIsRejected() {
        final SQLBuilder builder = new SQLBuilder();

        assertThrows(IllegalArgumentException.class, () -> builder.addPredicate("(o.hierarchy like ?)", List.of()));
        assertThrows(IllegalArgumentException.class, () -> builder.addPredicate("(o.hierarchy like ?)", List.of("a", "b")));
        assertThrows(IllegalArgumentException.class, () -> builder.addPredicate(" ", List.of()));
    }
}
