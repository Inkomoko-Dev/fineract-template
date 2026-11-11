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
package org.apache.fineract.portfolio.creditdecision.data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionData.EffectiveRuleData;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionData.PolicyValueData;

/**
 * The eligibility policy a profile version publishes for the decision engine: limits, cutoffs and scorecard weights,
 * set once for everyone, per country (by currency) or per loan product. A narrower setting wins over a wider one.
 *
 * <p>
 * The starting values are the ones in the Credit Scoring Process Flow (Option A): the knockout rules (age 18 or more,
 * no more than 30 days past due, no arrears, at least 6 months in business), the scorecard weights (internal repayment
 * history 40%, bureau history 30%, business financials 20%, demographics 10%) and the risk tiers (low from 750, medium
 * from 650, on a scale of 0 to 1000). The engine owns the scoring; this only holds what Credit Risk has approved.
 */
public final class CreditDecisionPolicy {

    public static final String GLOBAL = "GLOBAL";
    public static final String COUNTRY = "COUNTRY";
    public static final String PRODUCT = "PRODUCT";
    public static final Set<String> SCOPES = Set.of(GLOBAL, COUNTRY, PRODUCT);
    /** The scope key used for the one global setting, so it can be part of a unique key. */
    public static final String EVERYONE = "*";

    public static final String MIN_LOAN_AMOUNT = "minLoanAmount";
    public static final String MAX_LOAN_AMOUNT = "maxLoanAmount";
    public static final String MIN_LOAN_CYCLE = "minLoanCycle";
    public static final String MAX_DPD = "maxDaysPastDue";
    public static final String ARREARS_TOLERANCE = "arrearsTolerance";
    public static final String MAX_CONCURRENT_LOANS = "maxConcurrentLoans";
    public static final String MIN_AGE = "minAge";
    public static final String MIN_MONTHS_IN_BUSINESS = "minMonthsInBusiness";
    public static final String LOW_RISK_MIN_SCORE = "lowRiskMinScore";
    public static final String MEDIUM_RISK_MIN_SCORE = "mediumRiskMinScore";
    public static final String WEIGHT_INTERNAL = "weightInternalHistory";
    public static final String WEIGHT_BUREAU = "weightBureauHistory";
    public static final String WEIGHT_FINANCIAL = "weightBusinessFinancials";
    public static final String WEIGHT_DEMOGRAPHIC = "weightDemographics";

    /** The top of the score scale: the weights split it 40/30/20/10 into the sub-scores in the process flow. */
    public static final int MAX_SCORE = 1000;

    /** The countries the policy can be set for, by the currency their loans are in. */
    private static final Map<String, String> COUNTRIES = new LinkedHashMap<>();

    static {
        COUNTRIES.put("RWF", "Rwanda");
        COUNTRIES.put("KES", "Kenya");
        COUNTRIES.put("ETB", "Ethiopia");
        COUNTRIES.put("SSP", "South Sudan");
        COUNTRIES.put("XAF", "Central Africa (CFA franc)");
    }

    public enum Unit {
        AMOUNT, COUNT, DAYS, MONTHS, YEARS, SCORE, PERCENT;

        boolean whole() {
            return this != AMOUNT;
        }
    }

    /** One setting a profile version can hold. */
    public static final class RuleDef {

        private final String key;
        private final String group;
        private final String label;
        private final String description;
        private final Unit unit;
        private final BigDecimal starter;

        RuleDef(final String key, final String group, final String label, final String description, final Unit unit,
                final String starter) {
            this.key = key;
            this.group = group;
            this.label = label;
            this.description = description;
            this.unit = unit;
            this.starter = starter == null ? null : new BigDecimal(starter);
        }

        public String getKey() {
            return key;
        }

        public String getGroup() {
            return group;
        }

        public String getLabel() {
            return label;
        }

        public String getDescription() {
            return description;
        }

        public Unit getUnit() {
            return unit;
        }

        /** The value a new profile starts with for everyone, or null when there is no starting value. */
        public BigDecimal getStarter() {
            return starter;
        }
    }

    public static final String GROUP_LIMITS = "Limits";
    public static final String GROUP_KNOCKOUTS = "Knockout rules";
    public static final String GROUP_TIERS = "Risk tiers";
    public static final String GROUP_WEIGHTS = "Scorecard weights";

    private static final List<RuleDef> RULES = List.of(
            new RuleDef(MIN_LOAN_AMOUNT, GROUP_LIMITS, "Smallest loan", "Smallest amount that can be applied for, in the loan currency.",
                    Unit.AMOUNT, null),
            new RuleDef(MAX_LOAN_AMOUNT, GROUP_LIMITS, "Largest loan",
                    "Largest amount that can be applied for, in the loan currency. Set it per country to cap each currency.", Unit.AMOUNT,
                    null),
            new RuleDef(MIN_LOAN_CYCLE, GROUP_LIMITS, "Loan cycles completed for automatic approval",
                    "Loans a client must have fully repaid before an automatic approval is allowed (repeat clients).", Unit.COUNT, null),
            new RuleDef(MAX_CONCURRENT_LOANS, GROUP_LIMITS, "Open loans allowed",
                    "Most other loans a client may have open with Inkomoko when applying.", Unit.COUNT, null),
            new RuleDef(MIN_AGE, GROUP_KNOCKOUTS, "Youngest applicant", "Applicants younger than this are not eligible.", Unit.YEARS, "18"),
            new RuleDef(MAX_DPD, GROUP_KNOCKOUTS, "Days past due allowed",
                    "Worst delay on any internal loan, in days, above which the applicant is not eligible.", Unit.DAYS, "30"),
            new RuleDef(ARREARS_TOLERANCE, GROUP_KNOCKOUTS, "Arrears tolerated",
                    "Amount overdue on other active loans up to which the applicant is still eligible.", Unit.AMOUNT, "0"),
            new RuleDef(MIN_MONTHS_IN_BUSINESS, GROUP_KNOCKOUTS, "Shortest time in business",
                    "Months the business must have been operating.", Unit.MONTHS, "6"),
            new RuleDef(LOW_RISK_MIN_SCORE, GROUP_TIERS, "Low risk from score",
                    "Scores from here up are the low risk tier, eligible for the highest limits and automatic approval.", Unit.SCORE,
                    "750"),
            new RuleDef(MEDIUM_RISK_MIN_SCORE, GROUP_TIERS, "Medium risk from score",
                    "Scores from here up to the low risk cutoff are the medium risk tier. Lower scores are high risk.", Unit.SCORE, "650"),
            new RuleDef(WEIGHT_INTERNAL, GROUP_WEIGHTS, "Internal repayment history", "Share of the score, in percent.", Unit.PERCENT,
                    "40"),
            new RuleDef(WEIGHT_BUREAU, GROUP_WEIGHTS, "Credit bureau history", "Share of the score, in percent.", Unit.PERCENT, "30"),
            new RuleDef(WEIGHT_FINANCIAL, GROUP_WEIGHTS, "Business cash flow and financials", "Share of the score, in percent.",
                    Unit.PERCENT, "20"),
            new RuleDef(WEIGHT_DEMOGRAPHIC, GROUP_WEIGHTS, "Borrower profile", "Share of the score, in percent.", Unit.PERCENT, "10"));

    private static final Map<String, RuleDef> BY_KEY = new LinkedHashMap<>();

    static {
        for (final RuleDef def : RULES) {
            BY_KEY.put(def.key, def);
        }
    }

    private static final List<String> WEIGHT_KEYS = List.of(WEIGHT_INTERNAL, WEIGHT_BUREAU, WEIGHT_FINANCIAL, WEIGHT_DEMOGRAPHIC);

    private CreditDecisionPolicy() {}

    public static List<RuleDef> rules() {
        return RULES;
    }

    public static Map<String, String> countries() {
        return COUNTRIES;
    }

    public static boolean isKnown(final String key) {
        return key != null && BY_KEY.containsKey(key);
    }

    public static RuleDef rule(final String key) {
        return BY_KEY.get(key);
    }

    /** What a new profile starts with: the process flow's values, for everyone. */
    public static List<PolicyValueData> starter() {
        final List<PolicyValueData> out = new ArrayList<>();
        for (final RuleDef def : RULES) {
            if (def.starter != null) {
                out.add(new PolicyValueData(GLOBAL, EVERYONE, def.key, def.starter));
            }
        }
        return out;
    }

    /**
     * Puts values in a fixed order with the scope key spelled one way, so two copies of the same policy compare equal
     * and are shown the same. Values are not checked here.
     */
    public static List<PolicyValueData> normalise(final List<PolicyValueData> given) {
        final List<PolicyValueData> out = new ArrayList<>();
        if (given != null) {
            for (final PolicyValueData v : given) {
                if (v == null) {
                    continue;
                }
                final String scope = v.getScope() == null ? "" : v.getScope().trim().toUpperCase(Locale.ROOT);
                String key = v.getScopeKey() == null ? "" : v.getScopeKey().trim();
                if (GLOBAL.equals(scope)) {
                    key = EVERYONE;
                } else if (COUNTRY.equals(scope)) {
                    key = key.toUpperCase(Locale.ROOT);
                }
                out.add(new PolicyValueData(scope, key, v.getKey() == null ? "" : v.getKey().trim(),
                        v.getValue() == null ? null : v.getValue().stripTrailingZeros()));
            }
        }
        out.sort((a, b) -> {
            final int scope = Integer.compare(scopeOrder(a.getScope()), scopeOrder(b.getScope()));
            if (scope != 0) {
                return scope;
            }
            final int key = a.getScopeKey().compareTo(b.getScopeKey());
            return key != 0 ? key : Integer.compare(ruleOrder(a.getKey()), ruleOrder(b.getKey()));
        });
        return out;
    }

    private static int scopeOrder(final String scope) {
        return GLOBAL.equals(scope) ? 0 : COUNTRY.equals(scope) ? 1 : 2;
    }

    private static int ruleOrder(final String key) {
        int i = 0;
        for (final RuleDef def : RULES) {
            if (def.key.equals(key)) {
                return i;
            }
            i++;
        }
        return i;
    }

    /**
     * Problems that make even a draft unsaveable: a setting that is not in the catalogue, a scope or country that does
     * not exist, a missing or negative number, or a fraction where a whole number is needed. Keyed by where it is.
     */
    public static Map<String, String> structuralProblems(final List<PolicyValueData> normalised) {
        final Map<String, String> problems = new LinkedHashMap<>();
        final Set<String> seen = new java.util.HashSet<>();
        for (final PolicyValueData v : normalised) {
            final String where = where(v);
            final RuleDef def = BY_KEY.get(v.getKey());
            if (def == null) {
                problems.put(where, "'" + v.getKey() + "' is not a policy setting");
                continue;
            }
            if (!SCOPES.contains(v.getScope())) {
                problems.put(where, "Set it for everyone, a country or a product");
                continue;
            }
            if (COUNTRY.equals(v.getScope()) && !COUNTRIES.containsKey(v.getScopeKey())) {
                problems.put(where, "'" + v.getScopeKey() + "' is not a country the policy can be set for");
                continue;
            }
            if (PRODUCT.equals(v.getScope()) && !v.getScopeKey().matches("\\d+")) {
                problems.put(where, "Choose a loan product");
                continue;
            }
            if (!seen.add(where)) {
                problems.put(where, "Set more than once");
                continue;
            }
            if (v.getValue() == null) {
                problems.put(where, "Enter a number");
            } else if (v.getValue().signum() < 0) {
                problems.put(where, def.label + " cannot be negative");
            } else if (def.unit.whole() && v.getValue().scale() > 0) {
                problems.put(where, def.label + " must be a whole number");
            } else if (def.unit == Unit.PERCENT && v.getValue().compareTo(BigDecimal.valueOf(100)) > 0) {
                problems.put(where, def.label + " cannot be more than 100%");
            } else if (def.unit == Unit.SCORE && v.getValue().compareTo(BigDecimal.valueOf(MAX_SCORE)) > 0) {
                problems.put(where, def.label + " cannot be more than " + MAX_SCORE);
            }
        }
        return problems;
    }

    /**
     * What must be true before a version can be submitted: each scope that sets any scorecard weight sets all four and
     * they add up to 100%; a smallest loan is not above the largest; the medium risk cutoff is below the low risk one;
     * and a product or country setting refers to a product the version covers.
     */
    public static Map<String, String> submitProblems(final List<PolicyValueData> normalised, final List<Long> productIds,
            final Map<Long, String> currencyByProduct) {
        final Map<String, String> problems = new LinkedHashMap<>(structuralProblems(normalised));
        if (!problems.isEmpty()) {
            return problems;
        }
        // the knockout rules, risk cutoffs and weights must be set for everyone, so no product is left without them
        for (final RuleDef def : RULES) {
            if (def.starter != null
                    && normalised.stream().noneMatch(v -> GLOBAL.equals(v.getScope()) && def.key.equals(v.getKey()))) {
                problems.put("rule." + GLOBAL + "." + EVERYONE + "." + def.key, def.label + " must be set for everyone");
            }
        }
        final Map<String, Map<String, BigDecimal>> byScope = new LinkedHashMap<>();
        for (final PolicyValueData v : normalised) {
            byScope.computeIfAbsent(v.getScope() + ":" + v.getScopeKey(), k -> new LinkedHashMap<>()).put(v.getKey(), v.getValue());
            if (PRODUCT.equals(v.getScope()) && (productIds == null || !productIds.contains(Long.valueOf(v.getScopeKey())))) {
                problems.put(where(v), "This product is not covered by this version");
            }
        }
        byScope.forEach((scope, values) -> {
            final String name = scopeName(scope);
            if (WEIGHT_KEYS.stream().anyMatch(values::containsKey)) {
                BigDecimal sum = BigDecimal.ZERO;
                boolean all = true;
                for (final String key : WEIGHT_KEYS) {
                    if (values.containsKey(key)) {
                        sum = sum.add(values.get(key));
                    } else {
                        all = false;
                    }
                }
                if (!all) {
                    problems.put("weights." + scope, name + ": set all four scorecard weights, or none");
                } else if (sum.compareTo(BigDecimal.valueOf(100)) != 0) {
                    problems.put("weights." + scope, name + ": the scorecard weights add up to " + sum.toPlainString() + "%, they must add up to 100%");
                }
            }
        });
        problems.putAll(orderProblems(byScope));
        // a product's own and its country's settings must still make sense once they are combined
        if (productIds != null && currencyByProduct != null) {
            for (final Long productId : productIds) {
                final Map<String, BigDecimal> effective = effectiveValues(normalised, productId, currencyByProduct.get(productId));
                final String clash = clash(effective);
                if (clash != null && !problems.containsKey("effective." + productId)) {
                    problems.put("effective." + productId, "Product " + productId + ": " + clash);
                }
            }
        }
        return problems;
    }

    private static Map<String, String> orderProblems(final Map<String, Map<String, BigDecimal>> byScope) {
        final Map<String, String> problems = new LinkedHashMap<>();
        byScope.forEach((scope, values) -> {
            final String clash = clash(values);
            if (clash != null) {
                problems.put("order." + scope, scopeName(scope) + ": " + clash);
            }
        });
        return problems;
    }

    private static String clash(final Map<String, BigDecimal> values) {
        final BigDecimal min = values.get(MIN_LOAN_AMOUNT), max = values.get(MAX_LOAN_AMOUNT);
        if (min != null && max != null && min.compareTo(max) > 0) {
            return "the smallest loan is above the largest loan";
        }
        final BigDecimal medium = values.get(MEDIUM_RISK_MIN_SCORE), low = values.get(LOW_RISK_MIN_SCORE);
        if (medium != null && low != null && medium.compareTo(low) >= 0) {
            return "the medium risk cutoff must be below the low risk cutoff";
        }
        return null;
    }

    private static String where(final PolicyValueData v) {
        return "rule." + v.getScope() + "." + v.getScopeKey() + "." + v.getKey();
    }

    private static String scopeName(final String scope) {
        final String[] parts = scope.split(":", 2);
        if (GLOBAL.equals(parts[0])) {
            return "Everyone";
        }
        if (COUNTRY.equals(parts[0])) {
            return COUNTRIES.getOrDefault(parts[1], parts[1]);
        }
        return "Product " + parts[1];
    }

    /** Each setting as it applies to one product: its own value, else its country's (by currency), else the global one. */
    public static List<EffectiveRuleData> effective(final List<PolicyValueData> normalised, final Long productId, final String currency) {
        final List<EffectiveRuleData> out = new ArrayList<>();
        for (final RuleDef def : RULES) {
            PolicyValueData chosen = null;
            for (final PolicyValueData v : normalised) {
                if (!def.key.equals(v.getKey())) {
                    continue;
                }
                if (PRODUCT.equals(v.getScope()) && productId != null && v.getScopeKey().equals(String.valueOf(productId))) {
                    chosen = v;
                    break;
                }
                if (COUNTRY.equals(v.getScope()) && currency != null && v.getScopeKey().equalsIgnoreCase(currency)
                        && (chosen == null || GLOBAL.equals(chosen.getScope()))) {
                    chosen = v;
                } else if (GLOBAL.equals(v.getScope()) && chosen == null) {
                    chosen = v;
                }
            }
            if (chosen != null) {
                out.add(new EffectiveRuleData(def.key, chosen.getValue(), chosen.getScope(), chosen.getScopeKey()));
            }
        }
        return out;
    }

    public static Map<String, BigDecimal> effectiveValues(final List<PolicyValueData> normalised, final Long productId,
            final String currency) {
        final Map<String, BigDecimal> out = new LinkedHashMap<>();
        for (final EffectiveRuleData e : effective(normalised, productId, currency)) {
            out.put(e.getKey(), e.getValue());
        }
        return out;
    }
}
