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
package org.apache.fineract.portfolio.loanclassification.data;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Standard CBS loan classifications. Codes 1-6 are fixed; 0 and null are never valid stored values.
 */
public enum LoanClassificationCodes {

    NORMAL(1, "Normal/Performing"), //
    WATCH(2, "Watch"), //
    SUBSTANDARD(3, "Substandard"), //
    DOUBTFUL(4, "Doubtful"), //
    LOSS(5, "Loss"), //
    WRITE_OFF(6, "Write-off");

    private final int code;
    private final String label;

    LoanClassificationCodes(final int code, final String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() {
        return this.code;
    }

    public String getLabel() {
        return this.label;
    }

    public static boolean isValid(final Integer classification) {
        return classification != null && classification >= 1 && classification <= 6;
    }

    public static String labelOf(final Integer classification) {
        if (classification == null) {
            return null;
        }
        for (final LoanClassificationCodes value : values()) {
            if (value.code == classification) {
                return value.label;
            }
        }
        return null;
    }

    public static List<LoanClassificationCodes> arrearsCodes() {
        return Collections.unmodifiableList(Arrays.asList(NORMAL, WATCH, SUBSTANDARD, DOUBTFUL, LOSS));
    }
}
