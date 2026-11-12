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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Everything the decision engine can be given. A decision profile only chooses from this list; it never decides
 * anything. Adding an item here is how a newly connected data source becomes available to profiles.
 */
public final class CreditDecisionCatalogue {

    /** What to do when a switched-on parameter has no value for an application. */
    public static final String MISSING_BLOCK = "block";
    public static final String MISSING_NULL = "null";
    public static final String MISSING_OMIT = "omit";
    public static final Set<String> MISSING_POLICIES = Set.of(MISSING_BLOCK, MISSING_NULL, MISSING_OMIT);

    /** Parameters switched on for a brand new profile that is not copied from another one. */
    public static final List<String> STARTER_SET = List.of("amount", "term", "currency", "bscore", "blist");

    private static final List<CreditDecisionData.CatalogueGroup> GROUPS;
    private static final Map<String, CreditDecisionData.CatalogueItem> ITEMS = new LinkedHashMap<>();

    static {
        final List<CreditDecisionData.CatalogueGroup> groups = new ArrayList<>();
        groups.add(group("Application", "Loan application",
                item("amount", "requestedAmount", "Requested amount", "Amount the customer applied for", MISSING_BLOCK),
                item("term", "termMonths", "Loan term", "Requested term in months", MISSING_BLOCK),
                item("currency", "currency", "Currency", "Currency of the product", MISSING_BLOCK),
                item("purpose", "loanPurpose", "Loan purpose", "Stated use of the loan", MISSING_OMIT),
                item("channel", "channel", "Application channel", "USSD, mobile app or branch", MISSING_OMIT)));
        groups.add(group("Applicant", "CBS client record", item("age", "applicantAge", "Age", "In completed years", MISSING_NULL),
                item("tenure", "customerTenureMonths", "Months as a customer", "Since the client was first registered", MISSING_NULL),
                item("sector", "businessSector", "Business sector", "Main activity of the business", MISSING_NULL),
                item("kyc", "kycStatus", "KYC status", "Verified, pending or rejected", MISSING_BLOCK),
                item("resid", "countryOfResidence", "Country of residence", "Country on the client's active address", MISSING_NULL),
                item("bizMonths", "monthsInBusiness", "Months in business", "Since the business was started", MISSING_NULL)));
        groups.add(group("Loan history", "CBS loan accounts",
                item("cycles", "completedCycles", "Completed loan cycles", "Loans fully repaid with Inkomoko", MISSING_NULL),
                item("dpd", "maxDaysPastDue12m", "Max days past due", "Worst delay in the last 12 months", MISSING_NULL),
                item("late", "lateInstalments12m", "Late instalments", "Count in the last 12 months", MISSING_NULL),
                item("arrears", "currentArrears", "Current arrears", "Amount overdue today", MISSING_BLOCK),
                item("activeInt", "activeLoans", "Active loans with Inkomoko", "Open loan accounts", MISSING_NULL),
                item("prev", "previousLoanStatus", "Previous loan status", "Closed, written off or none", MISSING_NULL),
                item("wo", "writtenOff24m", "Written off (24 months)", "Yes or no", MISSING_NULL)));
        groups.add(group("Credit bureau", "CBS bureau report",
                item("bscore", "bureauScore", "Bureau score", "Score on the latest bureau report", MISSING_BLOCK),
                item("blist", "bureauListed", "Listed as defaulter", "Flag on the bureau report", MISSING_BLOCK),
                item("bactive", "bureauActiveLoans", "Active loans at other lenders", "From the bureau report", MISSING_NULL),
                item("bdate", "bureauReportDate", "Report date", "When the bureau report was pulled", MISSING_NULL),
                item("benq", "bureauEnquiryReference", "Enquiry reference", "The bureau's reference for the enquiry", MISSING_NULL),
                item("bfraud", "bureauFraudAlert", "Fraud alert", "Fraud recorded against the client on the bureau report",
                        MISSING_NULL),
                item("bbank", "bureauBankruptcy", "Bankruptcy", "Not held yet: the stored bureau report has no bankruptcy field",
                        MISSING_OMIT)));
        groups.add(group("Due diligence", "IMS and SurveyCTO",
                item("ddDone", "dueDiligenceCompleted", "Due diligence completed", "Yes or no", MISSING_BLOCK),
                item("ddRes", "dueDiligenceResult", "Due diligence result", "Recommended amount and finding", MISSING_NULL)));
        GROUPS = Collections.unmodifiableList(groups);
        GROUPS.forEach(g -> g.getItems().forEach(i -> ITEMS.put(i.getKey(), i)));
    }

    private CreditDecisionCatalogue() {}

    public static List<CreditDecisionData.CatalogueGroup> groups() {
        return GROUPS;
    }

    public static Map<String, CreditDecisionData.CatalogueItem> items() {
        return Collections.unmodifiableMap(ITEMS);
    }

    public static boolean isKnown(final String key) {
        return ITEMS.containsKey(key);
    }

    public static CreditDecisionData.CatalogueItem find(final String key) {
        return ITEMS.get(key);
    }

    /** Every catalogue item as a version parameter, with only the given keys switched on. */
    public static List<CreditDecisionData.ParamData> params(final Map<String, Boolean> switchedOn) {
        final List<CreditDecisionData.ParamData> out = new ArrayList<>();
        for (final CreditDecisionData.CatalogueItem item : ITEMS.values()) {
            out.add(new CreditDecisionData.ParamData(item.getKey(), Boolean.TRUE.equals(switchedOn.get(item.getKey())),
                    item.getDefaultField(), item.getDefaultMissing()));
        }
        return out;
    }

    public static List<CreditDecisionData.ParamData> starterParams() {
        final Map<String, Boolean> on = new LinkedHashMap<>();
        STARTER_SET.forEach(k -> on.put(k, Boolean.TRUE));
        return params(on);
    }

    private static CreditDecisionData.CatalogueGroup group(final String name, final String source,
            final CreditDecisionData.CatalogueItem... items) {
        return new CreditDecisionData.CatalogueGroup(name, source, List.of(items));
    }

    private static CreditDecisionData.CatalogueItem item(final String key, final String field, final String label, final String description,
            final String missing) {
        return new CreditDecisionData.CatalogueItem(key, field, label, description, missing);
    }
}
