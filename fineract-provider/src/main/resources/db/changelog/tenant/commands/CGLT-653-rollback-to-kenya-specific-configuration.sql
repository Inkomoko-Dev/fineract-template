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

-- Rollback script to revert from generic entity-based configuration back to Kenya-specific configuration
-- This can be used if the new generic framework needs to be rolled back

-- Step 1: Restore original descriptions for Kenya-specific configuration keys
UPDATE `c_configuration` 
SET `description` = 'Kenya Capital office name for disbursement defaults'
WHERE `name` = 'kenya-capital-office-name';

UPDATE `c_configuration` 
SET `description` = 'Default department name for Kenya Capital disbursements'
WHERE `name` = 'kenya-capital-default-department-name';

UPDATE `c_configuration` 
SET `description` = 'Budget code name for Kenya Capital investments'
WHERE `name` = 'investments-budget-code-name';

UPDATE `c_configuration` 
SET `description` = 'Enable Kenya Capital disbursement defaults'
WHERE `name` = 'kenya-capital-disbursement-defaults-enabled';

-- Step 2: Remove the new generic configuration keys
DELETE FROM `c_configuration` WHERE `name` = 'entity-disbursement-defaults-enabled';
DELETE FROM `c_configuration` WHERE `name` = 'entity-disbursement-defaults-config';

-- Step 3: Ensure Kenya-specific configuration keys exist and have correct values
-- These should already exist from the original implementation
INSERT INTO `c_configuration` (`name`, `enabled`, `value`, `description`, `is_trap_door`)
VALUES ('kenya-capital-disbursement-defaults-enabled', 1, 1, 'Enable Kenya Capital disbursement defaults', 0)
ON DUPLICATE KEY UPDATE `enabled` = 1, `value` = 1;

INSERT INTO `c_configuration` (`name`, `enabled`, `string_value`, `description`, `is_trap_door`)
VALUES ('kenya-capital-office-name', 1, 'Inkomoko - Capital Kenya Limited', 'Kenya Capital office name for disbursement defaults', 0)
ON DUPLICATE KEY UPDATE `enabled` = 1, `string_value` = 'Inkomoko - Capital Kenya Limited';

INSERT INTO `c_configuration` (`name`, `enabled`, `string_value`, `description`, `is_trap_door`)
VALUES ('kenya-capital-default-department-name', 1, 'Investment', 'Default department name for Kenya Capital disbursements', 0)
ON DUPLICATE KEY UPDATE `enabled` = 1, `string_value` = 'Investment';

INSERT INTO `c_configuration` (`name`, `enabled`, `string_value`, `description`, `is_trap_door`)
VALUES ('investments-budget-code-name', 1, 'InvestmentsBudget', 'Budget code name for Kenya Capital investments', 0)
ON DUPLICATE KEY UPDATE `enabled` = 1, `string_value` = 'InvestmentsBudget';