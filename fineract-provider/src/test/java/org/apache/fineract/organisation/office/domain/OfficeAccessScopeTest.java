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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class OfficeAccessScopeTest {

    private static final String KIGALI = ".1.2.";
    private static final String KIGALI_B = ".1.2.5.";
    private static final String KIGALI_C = ".1.2.6.";
    private static final String NAIROBI = ".1.3.";

    @Test
    @DisplayName("A hierarchical scope over one office covers that office and everything beneath it")
    public void hierarchicalScopeCoversDescendants() {
        final OfficeAccessScope scope = OfficeAccessScope.hierarchical(List.of(KIGALI));

        assertTrue(scope.isIncludeDescendants());
        assertEquals(List.of(KIGALI), scope.getHierarchies());
        assertTrue(scope.covers(KIGALI));
        assertTrue(scope.covers(KIGALI_B));
        assertTrue(scope.covers(KIGALI_C));
        assertFalse(scope.covers(NAIROBI));
    }

    @Test
    @DisplayName("A hierarchical scope renders one bound LIKE placeholder per office")
    public void hierarchicalScopeRendersLikePredicates() {
        final OfficeAccessScope scope = OfficeAccessScope.hierarchical(Arrays.asList(KIGALI_C, KIGALI_B));
        final OfficeAccessPredicate predicate = scope.predicate("o.hierarchy");

        assertEquals("(o.hierarchy like ? or o.hierarchy like ?)", predicate.getSql());
        assertEquals(List.of(".1.2.5.%", ".1.2.6.%"), predicate.getParameters());
    }

    @Test
    @DisplayName("Bound values sit in the statement's placeholder order")
    public void argumentsFollowTheOrderOfThePlaceholders() {
        final OfficeAccessPredicate predicate = OfficeAccessScope.hierarchical(List.of(KIGALI)).predicate("o.hierarchy");

        assertArrayEquals(new Object[] { ".1.2.%" }, predicate.getArguments());
        assertArrayEquals(new Object[] { 7L, ".1.2.%" }, predicate.argumentsPrecededBy(7L));
        assertArrayEquals(new Object[] { ".1.2.%", 7L, 300 }, predicate.argumentsFollowedBy(7L, 300));
    }

    @Test
    @DisplayName("A named predicate numbers its placeholders and maps each to its value")
    public void namedPredicateNumbersItsPlaceholders() {
        final NamedOfficeAccessPredicate predicate = OfficeAccessScope.hierarchical(Arrays.asList(KIGALI_B, NAIROBI))
                .namedPredicate("officeHierarchy", "of.hierarchy");

        assertEquals("(of.hierarchy like :officeHierarchy0 or of.hierarchy like :officeHierarchy1)", predicate.getSql());
        assertEquals(Map.of("officeHierarchy0", ".1.2.5.%", "officeHierarchy1", ".1.3.%"), predicate.getParameters());
    }

    @Test
    @DisplayName("Only the inlined form writes hierarchies into the SQL, for report SQL that cannot bind")
    public void inlinedPredicateWritesLiterals() {
        final OfficeAccessScope scope = OfficeAccessScope.hierarchical(Arrays.asList(KIGALI_C, KIGALI_B));

        assertEquals("(o.hierarchy like '.1.2.5.%' or o.hierarchy like '.1.2.6.%')", scope.inlinedPredicate("o.hierarchy"));
    }

    @Test
    @DisplayName("A column expression that is not a plain identifier is rejected")
    public void rejectsColumnThatIsNotAnIdentifier() {
        final OfficeAccessScope scope = OfficeAccessScope.hierarchical(List.of(KIGALI));

        assertThrows(IllegalArgumentException.class, () -> scope.predicate("o.hierarchy) or (1=1"));
        assertThrows(IllegalArgumentException.class, () -> scope.inlinedPredicate("o.hierarchy'"));
        assertThrows(IllegalArgumentException.class, () -> scope.namedPredicate("hierarchy", "o.hierarchy--"));
        assertThrows(IllegalArgumentException.class, () -> scope.namedPredicate("bad name", "o.hierarchy"));
    }

    @Test
    @DisplayName("A hierarchical scope ORs every office against every supplied column")
    public void hierarchicalScopeSpansMultipleColumns() {
        final OfficeAccessScope scope = OfficeAccessScope.hierarchical(Arrays.asList(KIGALI_B, NAIROBI));

        final OfficeAccessPredicate predicate = scope.predicate("o.hierarchy", "transferToOffice.hierarchy");

        assertEquals("(o.hierarchy like ? or o.hierarchy like ?"
                + " or transferToOffice.hierarchy like ? or transferToOffice.hierarchy like ?)", predicate.getSql());
        assertEquals(List.of(".1.2.5.%", ".1.3.%", ".1.2.5.%", ".1.3.%"), predicate.getParameters());
    }

    @Test
    @DisplayName("A hierarchical scope drops offices already covered by an assigned ancestor")
    public void hierarchicalScopeCollapsesRedundantDescendants() {
        final OfficeAccessScope scope = OfficeAccessScope.hierarchical(Arrays.asList(KIGALI_B, KIGALI, KIGALI_C, NAIROBI));

        assertEquals(Arrays.asList(KIGALI, NAIROBI), scope.getHierarchies());
    }

    @Test
    @DisplayName("An exact scope covers only the assigned offices themselves")
    public void exactScopeExcludesDescendants() {
        final OfficeAccessScope scope = OfficeAccessScope.exact(List.of(KIGALI));

        assertFalse(scope.isIncludeDescendants());
        assertTrue(scope.covers(KIGALI));
        assertFalse(scope.covers(KIGALI_B));
        assertEquals("(o.hierarchy = ?)", scope.predicate("o.hierarchy").getSql());
        assertEquals(List.of(KIGALI), scope.predicate("o.hierarchy").getParameters());
    }

    @Test
    @DisplayName("An exact scope keeps a child office assigned alongside its parent")
    public void exactScopeDoesNotCollapse() {
        final OfficeAccessScope scope = OfficeAccessScope.exact(Arrays.asList(KIGALI_B, KIGALI));

        assertEquals(Arrays.asList(KIGALI, KIGALI_B), scope.getHierarchies());
        assertEquals("(o.hierarchy = ? or o.hierarchy = ?)", scope.predicate("o.hierarchy").getSql());
        assertEquals(List.of(KIGALI, KIGALI_B), scope.predicate("o.hierarchy").getParameters());
    }

    @Test
    @DisplayName("The primary hierarchy is the first assigned office, for single valued call sites")
    public void primaryHierarchyIsTheFirstOffice() {
        assertEquals(KIGALI, OfficeAccessScope.hierarchical(Arrays.asList(NAIROBI, KIGALI)).primaryHierarchy());
    }

    @Test
    @DisplayName("The root office hierarchy grants the whole tree")
    public void rootHierarchyCoversEverything() {
        final OfficeAccessScope scope = OfficeAccessScope.hierarchical(List.of("."));

        assertTrue(scope.covers(NAIROBI));
        assertEquals("(o.hierarchy like ?)", scope.predicate("o.hierarchy").getSql());
        assertEquals(List.of(".%"), scope.predicate("o.hierarchy").getParameters());
    }

    @Test
    @DisplayName("A deeply nested office path is accepted and bound whole")
    public void acceptsDeeplyNestedHierarchies() {
        final String deep = ".1.2.3.4.5.6.7.8.9.10.";
        final OfficeAccessPredicate predicate = OfficeAccessScope.hierarchical(List.of(deep)).predicate("o.hierarchy");

        assertEquals("(o.hierarchy like ?)", predicate.getSql());
        assertEquals(List.of(deep + "%"), predicate.getParameters());
    }

    @Test
    @DisplayName("A hierarchy that is not a system generated office path is rejected")
    public void rejectsHierarchyThatIsNotAnOfficePath() {
        assertThrows(IllegalArgumentException.class, () -> OfficeAccessScope.hierarchical(List.of(".1.' or '1'='1")));
        assertThrows(IllegalArgumentException.class, () -> OfficeAccessScope.hierarchical(List.of("1.2.")));
        assertThrows(IllegalArgumentException.class, () -> OfficeAccessScope.hierarchical(List.of(".1.2")));
        assertThrows(IllegalArgumentException.class, () -> OfficeAccessScope.hierarchical(List.of(".1.2.;drop table m_office;--")));
        assertThrows(IllegalArgumentException.class, () -> OfficeAccessScope.hierarchical(List.of(".1.%")));
        assertThrows(IllegalArgumentException.class, () -> OfficeAccessScope.hierarchical(List.of(".1..")));
    }

    @Test
    @DisplayName("The standalone check guards callers that must inline a hierarchy, such as report SQL")
    public void requireOfficeHierarchyGuardsInliningCallers() {
        assertEquals(KIGALI, OfficeAccessScope.requireOfficeHierarchy(KIGALI));
        assertEquals(".", OfficeAccessScope.requireOfficeHierarchy("."));

        assertThrows(IllegalArgumentException.class, () -> OfficeAccessScope.requireOfficeHierarchy(null));
        assertThrows(IllegalArgumentException.class, () -> OfficeAccessScope.requireOfficeHierarchy(".1.' or '1'='1"));
        assertThrows(IllegalArgumentException.class, () -> OfficeAccessScope.requireOfficeHierarchy(".1.%"));
    }

    @Test
    @DisplayName("A scope with no offices is rejected")
    public void rejectsEmptyScope() {
        assertThrows(IllegalArgumentException.class, () -> OfficeAccessScope.hierarchical(Collections.emptyList()));
        assertThrows(IllegalArgumentException.class, () -> OfficeAccessScope.hierarchical(null));
    }

    @Test
    @DisplayName("A predicate cannot be rendered without a column")
    public void rejectsPredicateWithoutColumn() {
        final OfficeAccessScope scope = OfficeAccessScope.hierarchical(List.of(KIGALI));

        assertThrows(IllegalArgumentException.class, scope::predicate);
        assertThrows(IllegalArgumentException.class, scope::inlinedPredicate);
    }
}
