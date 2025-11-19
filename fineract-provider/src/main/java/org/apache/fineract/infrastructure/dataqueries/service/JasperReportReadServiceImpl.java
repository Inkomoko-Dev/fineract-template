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
package org.apache.fineract.infrastructure.dataqueries.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.service.MinIOStorageService;
import org.apache.fineract.infrastructure.dataqueries.domain.JasperReport;
import org.apache.fineract.infrastructure.dataqueries.domain.JasperReportRepository;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.Collection;
import java.util.Objects;
import java.util.Map;
import java.util.LinkedHashMap;


@Service
@Slf4j
@RequiredArgsConstructor
public class JasperReportReadServiceImpl implements ReadJasperReportingService{

    private final JasperReportRepository jasperReportRepository;
    private final PlatformSecurityContext context;
    private final MinIOStorageService minIOStorageService;
    private final JdbcTemplate jdbcTemplate;


    @Override
    public Collection<JasperReport> retrieveReportList(String status) {
        this.context.authenticatedUser();

        return jasperReportRepository.findAllByStatus(status);
    }

    @Override
    public JasperReport retrieveSignedReport(String reportId) {
        this.context.authenticatedUser();
        JasperReport jasperReport = jasperReportRepository.findById(Long.valueOf(reportId)).orElseThrow();

        if (Objects.equals(jasperReport.getStatus(), "APPROVED")){
            String presignedObjectUrl = minIOStorageService.getPresignedDocumentUrl(jasperReport.getFilePath());
            jasperReport.setFilePath(presignedObjectUrl);
        }

        return jasperReport;
    }

    @Override
    public InputStream downloadReport(String reportId) {
        this.context.authenticatedUser();
        JasperReport jasperReport = retrieveReport(reportId);

        if (Objects.equals(jasperReport.getStatus(), "APPROVED")){
            log.info("Downloading report {}", jasperReport.getFilePath());
            return minIOStorageService.downloadReport(jasperReport.getFilePath());
        }

        return null;
    }

    @Override
    public JasperReport retrieveReport(String reportId) {
        this.context.authenticatedUser();
        return jasperReportRepository.findById(Long.valueOf(reportId)).orElseThrow();
    }

    @Override
    public Collection<Object> retrieveReportApprovers() {
        final String sql = """
        SELECT DISTINCT u.id AS id,
                        CONCAT(u.firstname, ' ', u.lastname) AS fullname,
                        u.email
        FROM m_appuser u
        JOIN m_appuser_role ur ON ur.appuser_id = u.id
        JOIN m_role r ON r.id = ur.role_id
        JOIN m_role_permission rp ON rp.role_id = r.id
        JOIN m_permission p ON p.id = rp.permission_id
        WHERE p.code = 'APPROVE_JASPER_REPORT'
          AND u.enabled = true
        ORDER BY fullname
        """;

        return this.jdbcTemplate.query(sql, (rs, rowNum) -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", rs.getLong("id"));
            map.put("fullname", rs.getString("fullname"));
            map.put("email", rs.getString("email"));
            return map;
        });
    }
}

