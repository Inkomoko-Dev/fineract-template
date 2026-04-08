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
package org.apache.fineract.portfolio.client.service;

import lombok.RequiredArgsConstructor;
import org.apache.fineract.portfolio.client.data.RefBankData;
import org.apache.fineract.portfolio.client.domain.RefBank;
import org.apache.fineract.portfolio.client.domain.RefBankRepository;
import org.apache.fineract.portfolio.client.exception.RefBankNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RefBankReadPlatformServiceImpl implements RefBankReadPlatformService {

    private final RefBankRepository refBankRepository;

    @Override
    public List<RefBankData> retrieveAllBanks() {
        return refBankRepository.findAllByIsActiveTrueOrderByCountryAscBankNameAsc()
                .stream()
                .map(this::toData)
                .collect(Collectors.toList());
    }

    @Override
    public List<RefBankData> searchBanks(final String query) {
        if (query == null || query.trim().isEmpty()) {
            return retrieveAllBanks();
        }
        return refBankRepository.searchBanks(query.trim())
                .stream()
                .map(this::toData)
                .collect(Collectors.toList());
    }

    @Override
    public RefBankData retrieveBank(final Long bankId) {
        final RefBank bank = refBankRepository.findById(bankId)
                .orElseThrow(() -> new RefBankNotFoundException(bankId));
        return toData(bank);
    }

    private RefBankData toData(final RefBank bank) {
        return RefBankData.instance(
                bank.getId(),
                bank.getBankCode(),
                bank.getBankName(),
                bank.getCountry(),
                bank.isActive());
    }
}