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
package org.apache.fineract.portfolio.client.data;

import java.io.Serializable;
import java.math.BigDecimal;
import lombok.Getter;
import org.apache.fineract.portfolio.collateralmanagement.domain.ClientCollateralManagement;
import org.apache.fineract.portfolio.collateralmanagement.domain.ClientCollateralManagementAdditionalDetails;

public final class ClientCollateralManagementData implements Serializable {

    private final BigDecimal quantity;

    private final Long id;

    private final BigDecimal pctToBase;

    private final BigDecimal unitPrice;

    private final BigDecimal total;

    private final BigDecimal totalCollateral;

    private final String name;

    @Getter
    private final BigDecimal worthOfCollateral;

    private ClientCollateralManagementData(final BigDecimal quantity, final Long id, final BigDecimal pctToBase, final BigDecimal unitPrice,
            final BigDecimal total, final BigDecimal totalCollateral, final String name, BigDecimal worthOfCollateral) {
        this.id = id;
        this.pctToBase = pctToBase;
        this.total = total;
        this.totalCollateral = totalCollateral;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
        this.name = name;
        this.worthOfCollateral = worthOfCollateral;
    }

    public static ClientCollateralManagementData setCollateralValues(final ClientCollateralManagement clientCollateralManagements,
            final BigDecimal total, final BigDecimal totalCollateral, ClientCollateralManagementAdditionalDetails additionalDetails) {
        return new ClientCollateralManagementData(clientCollateralManagements.getQuantity(), clientCollateralManagements.getId(),
                clientCollateralManagements.getCollaterals().getPctToBase(), clientCollateralManagements.getCollaterals().getBasePrice(),
                total, totalCollateral, clientCollateralManagements.getCollaterals().getName(),
                additionalDetails != null ? additionalDetails.getWorthOfCollateral() : null);
    }

    public static ClientCollateralManagementData fromProjection(final Long id, final BigDecimal quantity, final BigDecimal pctToBase,
            final BigDecimal unitPrice, final BigDecimal total, final BigDecimal totalCollateral, final String name,
            final BigDecimal worthOfCollateral) {
        return new ClientCollateralManagementData(quantity, id, pctToBase, unitPrice, total, totalCollateral, name, worthOfCollateral);
    }

    public BigDecimal getQuantity() {
        return this.quantity;
    }

    public BigDecimal getTotal() {
        return this.total;
    }

    public BigDecimal getPctToBase() {
        return this.pctToBase;
    }

    public BigDecimal getTotalCollateral() {
        return this.totalCollateral;
    }

    public BigDecimal getUnitPrice() {
        return this.unitPrice;
    }

    public Long getId() {
        return this.id;
    }
}
