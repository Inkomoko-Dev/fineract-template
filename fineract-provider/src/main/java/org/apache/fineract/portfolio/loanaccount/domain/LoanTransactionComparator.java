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
package org.apache.fineract.portfolio.loanaccount.domain;

import java.time.OffsetDateTime;
import java.util.Comparator;

/**
 * Sort loan transactions by economic replay order.
 */
public class LoanTransactionComparator implements Comparator<LoanTransaction> {

    @Override
    public int compare(final LoanTransaction o1, final LoanTransaction o2) {
        int compareResult = o1.getTransactionDate().compareTo(o2.getTransactionDate());
        if (compareResult != 0) {
            return compareResult;
        }

        compareResult = Integer.compare(economicReplayOrder(o1), economicReplayOrder(o2));
        if (compareResult != 0) {
            return compareResult;
        }

        compareResult = compareNullableOffsetDateTime(createdDate(o1), createdDate(o2));
        if (compareResult != 0) {
            return compareResult;
        }

        if (o1.isWaiver() && o2.isNotWaiver()) {
            return -1;
        } else if (o1.isNotWaiver() && o2.isWaiver()) {
            return 1;
        }

        return compareNullableLong(o1.getId(), o2.getId());
    }

    private static int economicReplayOrder(final LoanTransaction transaction) {
        if (transaction.isDisbursement()) {
            return 0;
        } else if (transaction.isIncomePosting()) {
            return 1;
        } else if (transaction.isRepaymentAtDisbursement()) {
            return 2;
        } else if (transaction.isDisbursementChargeAdjustment()) {
            return 3;
        }
        return 4;
    }

    private static OffsetDateTime createdDate(final LoanTransaction transaction) {
        return transaction.getCreatedDate().orElse(null);
    }

    private static int compareNullableOffsetDateTime(final OffsetDateTime left, final OffsetDateTime right) {
        if (left == null && right == null) {
            return 0;
        } else if (left == null) {
            return 1;
        } else if (right == null) {
            return -1;
        }
        return left.compareTo(right);
    }

    private static int compareNullableLong(final Long left, final Long right) {
        if (left == null && right == null) {
            return 0;
        } else if (left == null) {
            return 1;
        } else if (right == null) {
            return -1;
        }
        return left.compareTo(right);
    }

}
