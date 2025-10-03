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
package org.apache.fineract.portfolio.address.domain;

import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.apache.fineract.infrastructure.codes.domain.CodeValue;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;
import org.apache.fineract.portfolio.client.domain.ClientAddress;

@Entity
@Getter
@Setter
@Table(name = "m_address")
public class Address extends AbstractPersistableCustom {

    /*
     * @OneToMany(mappedBy = "address", cascade = CascadeType.ALL) private List<ClientAddress> clientaddress = new
     * ArrayList<>();
     */

    @OneToMany(mappedBy = "address", cascade = CascadeType.ALL)
    private Set<ClientAddress> clientaddress;

    @Column(name = "location")
    private String location;

    @Column(name = "street")
    private String street;

    @Column(name = "address_line_1")
    private String addressLine1;

    @Column(name = "address_line_2")
    private String addressLine2;

    @Column(name = "address_line_3")
    private String addressLine3;

    @Column(name = "town_village")
    private String townVillage;

    @Column(name = "city")
    private String city;

    @Column(name = "county_district")
    private String countyDistrict;

    @ManyToOne
    @JoinColumn(name = "state_province_id")
    private CodeValue stateProvince;

    @ManyToOne
    @JoinColumn(name = "country_id")
    private CodeValue country;

    @Column(name = "postal_code")
    private String postalCode;

    @Column(name = "latitude")
    private BigDecimal latitude;

    @Column(name = "longitude")
    private BigDecimal longitude;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_on")
    private LocalDate createdOn;

    @Column(name = "updated_by")
    private String updatedBy;

    @Column(name = "updated_on")
    private LocalDate updatedOn;

    @ManyToOne
    @JoinColumn(name = "lga_id")
    private CodeValue lga;
    @Column(name = "physical_address_district")
    private String physicalAddressDistrict;
    @Column(name = "physical_address_sector")
    private String physicalAddressSector;
    @Column(name = "physical_address_cell")
    private String physicalAddressCell;

    @Column(name = "at_address_since")
    private LocalDate atAddressSince;

    private Address(final String location, final String street, final String addressLine1, final String addressLine2, final String addressLine3,
                    final String townVillage, final String city, final String countyDistrict, final CodeValue stateProvince,
                    final CodeValue country, final String postalCode, final BigDecimal latitude, final BigDecimal longitude, final String createdBy,
                    final LocalDate createdOn, final String updatedBy, final LocalDate updatedOn, final CodeValue lga,
                    final LocalDate atAddressSince, final String physicalAddressDistrict, final String physicalAddressSector,
                    final String physicalAddressCell) {
        this.location = location;
        this.street = street;
        this.addressLine1 = addressLine1;
        this.addressLine2 = addressLine2;
        this.addressLine3 = addressLine3;
        this.townVillage = townVillage;
        this.city = city;
        this.countyDistrict = countyDistrict;
        this.stateProvince = stateProvince;
        this.country = country;
        this.postalCode = postalCode;
        this.latitude = latitude;
        this.longitude = longitude;
        this.createdBy = createdBy;
        this.updatedBy = updatedBy;
        this.lga = lga;
        this.physicalAddressDistrict = physicalAddressDistrict;
        this.physicalAddressSector = physicalAddressSector;
        this.physicalAddressCell = physicalAddressCell;

        if (createdOn != null) {
            this.createdOn = createdOn;

        }

        if (updatedOn != null) {
            this.updatedOn = updatedOn;
        }

        this.atAddressSince = atAddressSince;

    }

    public Address() {

    }

    public static Address fromJson(final JsonCommand command, final CodeValue stateProvince, final CodeValue country) {

        final String location = command.stringValueOfParameterNamed("location");

        final String street = command.stringValueOfParameterNamed("street");

        final String addressLine1 = command.stringValueOfParameterNamed("addressLine1");

        final String addressLine2 = command.stringValueOfParameterNamed("addressLine2");

        final String addressLine3 = command.stringValueOfParameterNamed("addressLine3");

        final String townVillage = command.stringValueOfParameterNamed("townVillage");

        final String city = command.stringValueOfParameterNamed("city");

        final String countyDistrict = command.stringValueOfParameterNamed("countyDistrict");

        final String postalCode = command.stringValueOfParameterNamed("postalCode");

        final BigDecimal latitude = command.bigDecimalValueOfParameterNamed("latitude");

        final BigDecimal longitude = command.bigDecimalValueOfParameterNamed("longitude");

        final String createdBy = command.stringValueOfParameterNamed("createdBy");

        final LocalDate createdOn = command.localDateValueOfParameterNamed("createdOn");

        final String updatedBy = command.stringValueOfParameterNamed("updatedBy");

        final LocalDate updatedOn = command.localDateValueOfParameterNamed("updatedOn");

        final LocalDate atAddressSince = command.localDateValueOfParameterNamed("atAddressSince");

        final String physicalAddressDistrict = command.stringValueOfParameterNamed("physicalAddressDistrict");
        final String physicalAddressSector = command.stringValueOfParameterNamed("physicalAddressSector");
        final String physicalAddressCell = command.stringValueOfParameterNamed("physicalAddressCell");

        return new Address(location, street, addressLine1, addressLine2, addressLine3, townVillage, city, countyDistrict, stateProvince, country,
                postalCode, latitude, longitude, createdBy, createdOn, updatedBy, updatedOn, null, atAddressSince, physicalAddressDistrict,
                physicalAddressSector, physicalAddressCell);
    }

    public static Address fromJsonObject(final JsonObject jsonObject, final CodeValue state_province, final CodeValue country,
                                         final CodeValue lga) {
        String location = "";
        String street = "";
        String addressLine1 = "";
        String addressLine2 = "";
        String addressLine3 = "";
        String townVillage = "";
        String city = "";
        String countyDistrict = "";
        String postalCode = "";
        BigDecimal latitude = BigDecimal.ZERO;
        BigDecimal longitude = BigDecimal.ZERO;
        String createdBy = "";
        String updatedBy = "";
        LocalDate updatedOnDate = null;
        LocalDate createdOnDate = null;
        LocalDate atAddressSince = null;
        String physicalAddressDistrict = "";
        String physicalAddressSector = "";
        String physicalAddressCell = "";

        if (jsonObject.has("location")) {
            location = jsonObject.get("location").getAsString();
        }

        if (jsonObject.has("street")) {
            street = jsonObject.get("street").getAsString();
        }

        if (jsonObject.has("addressLine1")) {
            addressLine1 = jsonObject.get("addressLine1").getAsString();
        }
        if (jsonObject.has("addressLine2")) {

            addressLine2 = jsonObject.get("addressLine2").getAsString();
        }
        if (jsonObject.has("addressLine3")) {
            addressLine3 = jsonObject.get("addressLine3").getAsString();
        }
        if (jsonObject.has("townVillage")) {
            townVillage = jsonObject.get("townVillage").getAsString();
        }
        if (jsonObject.has("city")) {
            city = jsonObject.get("city").getAsString();
        }
        if (jsonObject.has("countyDistrict")) {
            countyDistrict = jsonObject.get("countyDistrict").getAsString();
        }
        if (jsonObject.has("postalCode")) {

            postalCode = jsonObject.get("postalCode").getAsString();
        }
        if (jsonObject.has("latitude")) {

            latitude = jsonObject.get("latitude").getAsBigDecimal();
        }
        if (jsonObject.has("longitude")) {

            longitude = jsonObject.get("longitude").getAsBigDecimal();
        }

        if (jsonObject.has("createdBy")) {
            createdBy = jsonObject.get("createdBy").getAsString();
        }
        if (jsonObject.has("createdOn")) {
            String createdOn = jsonObject.get("createdOn").getAsString();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            createdOnDate = LocalDate.parse(createdOn, formatter);

        }
        if (jsonObject.has("updatedBy")) {
            updatedBy = jsonObject.get("updatedBy").getAsString();
        }
        if (jsonObject.has("updatedOn")) {
            String updatedOn = jsonObject.get("updatedOn").getAsString();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            updatedOnDate = LocalDate.parse(updatedOn, formatter);
        }

        if (jsonObject.has("atAddressSince")) {
            String updatedOn = jsonObject.get("atAddressSince").getAsString();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            atAddressSince = LocalDate.parse(updatedOn, formatter);
        }
        if (jsonObject.has("physicalAddressDistrict")) {
            physicalAddressDistrict = jsonObject.get("physicalAddressDistrict").getAsString();
        }
        if (jsonObject.has("physicalAddressSector")) {
            physicalAddressSector = jsonObject.get("physicalAddressSector").getAsString();
        }
        if (jsonObject.has("physicalAddressCell")) {
            physicalAddressCell = jsonObject.get("physicalAddressCell").getAsString();
        }

        return new Address(location, street, addressLine1, addressLine2, addressLine3, townVillage, city, countyDistrict, state_province, country,
                postalCode, latitude, longitude, createdBy, createdOnDate, updatedBy, updatedOnDate, lga, atAddressSince,
                physicalAddressDistrict, physicalAddressSector, physicalAddressCell);
    }
}
