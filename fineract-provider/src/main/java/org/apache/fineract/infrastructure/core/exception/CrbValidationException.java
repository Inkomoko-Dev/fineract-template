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
package org.apache.fineract.infrastructure.core.exception;

public class CrbValidationException extends CrbException {

    private final String loanAccount;
    private final String fieldName;
    private final String fieldValue;
    private final String validationMessage;

    public CrbValidationException(
            String loanAccount,
            String fieldName,
            String fieldValue,
            String validationMessage,
            String callbackId) {

        super(validationMessage, callbackId);
        this.loanAccount = loanAccount;
        this.fieldName = fieldName;
        this.fieldValue = fieldValue;
        this.validationMessage = validationMessage;
    }

    @Override
    public String getUserMessage() {
        return String.format(
                "Loan %s rejected by TransUnion: %s (Field: %s, Value: %s)",
                loanAccount, validationMessage, fieldName, fieldValue
        );
    }

    public String getLoanAccount() {
        return loanAccount;
    }
}
