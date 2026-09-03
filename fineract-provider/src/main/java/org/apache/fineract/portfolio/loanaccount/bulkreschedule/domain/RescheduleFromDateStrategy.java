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
package org.apache.fineract.portfolio.loanaccount.bulkreschedule.domain;

/**
 * Strategy for deriving the reschedule-from date per loan in a bulk reschedule operation.
 * Since each loan has different installment dates, the date cannot be a single global value;
 * it must be computed per-loan using one of these strategies.
 */
public enum RescheduleFromDateStrategy {

    /** Use the due date of the very first installment on the loan schedule */
    FIRST_INSTALLMENT,

    /** Use the due date of the first installment that has not yet been fully paid */
    NEXT_UNPAID
}

