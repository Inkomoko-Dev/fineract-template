--
-- Licensed to the Apache Software Foundation (ASF) under one
-- or more contributor license agreements. See the NOTICE file
-- distributed with this work for additional information
-- regarding copyright ownership. The ASF licenses this file
-- to you under the Apache License, Version 2.0 (the
-- "License"); you may not use this file except in compliance
-- with the License. You may obtain a copy of the License at
--
-- http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing,
-- software distributed under the License is distributed on an
-- "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
-- KIND, either express or implied. See the License for the
-- specific language governing permissions and limitations
-- under the License.
--

-- CGLT-653: Migration script to convert Kenya-specific disbursement defaults configuration to generic entity-based configuration
-- This addresses the PR feedback about removing country-specific code from CBS

-- Step 1: Insert the new generic configuration key
INSERT INTO `c_configuration` (`name`, `enabled`, `value`, `description`, `is_trap_door`)
VALUES ('entity-disbursement-defaults-enabled', 1, 1, 'Enable generic entity-specific disbursement defaults (replaces country-specific Kenya Capital defaults)', 0)
ON DUPLICATE KEY UPDATE `value` = 1;

-- Step 2: Insert the new entity configuration JSON with Kenya Capital as the first entity
-- This maintains backward compatibility while moving to a generic framework
-- Use description column for JSON data as string_value column has limited size
INSERT INTO `c_configuration` (`name`, `enabled`, `string_value`, `description`, `is_trap_door`)
VALUES (
    'entity-disbursement-defaults-config',
    1,
    NULL,
    '[
      {
        "entityName": "Kenya Capital",
        "officeNames": ["Inkomoko - Capital Kenya Limited"],
        "defaultDepartmentName": "Investment",
        "budgetCodeName": "InvestmentsBudget",
        "budgetLocationPrefix": "Investments - "
      }
    ]',
    0
)
ON DUPLICATE KEY UPDATE `description` = '[
  {
    "entityName": "Kenya Capital",
    "officeNames": ["Inkomoko - Capital Kenya Limited"],
    "defaultDepartmentName": "Investment",
    "budgetCodeName": "InvestmentsBudget",
    "budgetLocationPrefix": "Investments - "
  }
]';

-- Step 3: Mark old Kenya-specific configuration keys as deprecated (optional - can be removed later)
-- We keep them for now to allow rollback if needed
UPDATE `c_configuration` 
SET `description` = '[DEPRECATED - Use entity-disbursement-defaults-config] Kenya Capital office name for disbursement defaults'
WHERE `name` = 'kenya-capital-office-name';

UPDATE `c_configuration` 
SET `description` = '[DEPRECATED - Use entity-disbursement-defaults-config] Default department name for Kenya Capital disbursements'
WHERE `name` = 'kenya-capital-default-department-name';

UPDATE `c_configuration` 
SET `description` = '[DEPRECATED - Use entity-disbursement-defaults-config] Budget code name for Kenya Capital investments'
WHERE `name` = 'investments-budget-code-name';

UPDATE `c_configuration` 
SET `description` = '[DEPRECATED - Use entity-disbursement-defaults-enabled] Enable Kenya Capital disbursement defaults'
WHERE `name` = 'kenya-capital-disbursement-defaults-enabled';

-- Step 4: Documentation note
-- CGLT-653: Migrated from country-specific Kenya Capital configuration to generic entity-based framework
-- The new configuration supports multiple entities (Kenya Capital, Rwanda Capital, etc.) via JSON configuration
-- Old configuration keys are preserved for backward compatibility but marked as deprecated