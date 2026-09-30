-- Complete tables used by the current JPA models without replacing existing data.
CREATE TABLE IF NOT EXISTS `system_settings` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `name` varchar(100) DEFAULT NULL,
    `active` boolean DEFAULT TRUE,
    `create_time` timestamp NULL DEFAULT NULL,
    `update_time` timestamp NULL DEFAULT NULL,
    `code` varchar(100) DEFAULT NULL,
    `label` varchar(255) DEFAULT NULL,
    `value` text,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `table_row` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `name` varchar(100) DEFAULT NULL,
    `active` boolean DEFAULT TRUE,
    `create_time` timestamp NULL DEFAULT NULL,
    `update_time` timestamp NULL DEFAULT NULL,
    `title` varchar(255) DEFAULT NULL,
    `checked` boolean DEFAULT FALSE,
    `properties` varchar(255) DEFAULT NULL,
    `type` varchar(100) DEFAULT NULL,
    `sorted` int DEFAULT NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `table_row_system_menu_relation` (
    `system_menu_id` bigint DEFAULT NULL,
    `table_row_id` bigint DEFAULT NULL,
    KEY `idx_table_row_menu` (`system_menu_id`),
    KEY `idx_table_row_id` (`table_row_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Add fields missing from older installations; preserve existing field values.
SET @grantforge_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'grantforge_menu' AND column_name = 'is_system'),
    'SELECT 1', 'ALTER TABLE `grantforge_menu` ADD COLUMN `is_system` boolean DEFAULT FALSE');
PREPARE grantforge_statement FROM @grantforge_ddl;
EXECUTE grantforge_statement;
DEALLOCATE PREPARE grantforge_statement;

-- Add fields missing from older installations; preserve existing field values.
SET @grantforge_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'grantforge_user' AND column_name = 'locked'),
    'SELECT 1', 'ALTER TABLE `grantforge_user` ADD COLUMN `locked` boolean DEFAULT FALSE');
PREPARE grantforge_statement FROM @grantforge_ddl;
EXECUTE grantforge_statement;
DEALLOCATE PREPARE grantforge_statement;

-- Add fields missing from older installations; preserve existing field values.
SET @grantforge_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'grantforge_user' AND column_name = 'is_system'),
    'SELECT 1', 'ALTER TABLE `grantforge_user` ADD COLUMN `is_system` boolean DEFAULT FALSE');
PREPARE grantforge_statement FROM @grantforge_ddl;
EXECUTE grantforge_statement;
DEALLOCATE PREPARE grantforge_statement;

-- Add fields missing from older installations; preserve existing field values.
SET @grantforge_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'grantforge_user' AND column_name = 'email'),
    'SELECT 1', 'ALTER TABLE `grantforge_user` ADD COLUMN `email` varchar(255) DEFAULT NULL');
PREPARE grantforge_statement FROM @grantforge_ddl;
EXECUTE grantforge_statement;
DEALLOCATE PREPARE grantforge_statement;

-- Widen text fields without truncating legacy values.
SET @grantforge_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'grantforge_user' AND column_name = 'email'
                  AND character_maximum_length < 255),
    'ALTER TABLE `grantforge_user` MODIFY COLUMN `email` varchar(255) DEFAULT NULL', 'SELECT 1');
PREPARE grantforge_statement FROM @grantforge_ddl;
EXECUTE grantforge_statement;
DEALLOCATE PREPARE grantforge_statement;
SET @grantforge_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'grantforge_menu' AND column_name = 'description'
                  AND character_maximum_length < 500),
    'ALTER TABLE `grantforge_menu` MODIFY COLUMN `description` varchar(500) DEFAULT NULL', 'SELECT 1');
PREPARE grantforge_statement FROM @grantforge_ddl;
EXECUTE grantforge_statement;
DEALLOCATE PREPARE grantforge_statement;

-- Align generated IDs with the entity identity mapping; invalid legacy IDs fail rather than being deleted.
SET @grantforge_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.table_constraints
            WHERE table_schema = DATABASE() AND table_name = 'icon' AND constraint_type = 'PRIMARY KEY'),
    'SELECT 1', 'ALTER TABLE `icon` ADD PRIMARY KEY (`id`)');
PREPARE grantforge_statement FROM @grantforge_ddl;
EXECUTE grantforge_statement;
DEALLOCATE PREPARE grantforge_statement;
ALTER TABLE `icon` MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT;

-- Align generated IDs with the entity identity mapping; invalid legacy IDs fail rather than being deleted.
SET @grantforge_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.table_constraints
            WHERE table_schema = DATABASE() AND table_name = 'icon_type' AND constraint_type = 'PRIMARY KEY'),
    'SELECT 1', 'ALTER TABLE `icon_type` ADD PRIMARY KEY (`id`)');
PREPARE grantforge_statement FROM @grantforge_ddl;
EXECUTE grantforge_statement;
DEALLOCATE PREPARE grantforge_statement;
ALTER TABLE `icon_type` MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT;

-- Align generated IDs with the entity identity mapping; invalid legacy IDs fail rather than being deleted.
SET @grantforge_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.table_constraints
            WHERE table_schema = DATABASE() AND table_name = 'icon_usage' AND constraint_type = 'PRIMARY KEY'),
    'SELECT 1', 'ALTER TABLE `icon_usage` ADD PRIMARY KEY (`id`)');
PREPARE grantforge_statement FROM @grantforge_ddl;
EXECUTE grantforge_statement;
DEALLOCATE PREPARE grantforge_statement;
ALTER TABLE `icon_usage` MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT;

-- Align generated IDs with the entity identity mapping; invalid legacy IDs fail rather than being deleted.
SET @grantforge_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.table_constraints
            WHERE table_schema = DATABASE() AND table_name = 'system_log' AND constraint_type = 'PRIMARY KEY'),
    'SELECT 1', 'ALTER TABLE `system_log` ADD PRIMARY KEY (`id`)');
PREPARE grantforge_statement FROM @grantforge_ddl;
EXECUTE grantforge_statement;
DEALLOCATE PREPARE grantforge_statement;
ALTER TABLE `system_log` MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT;

-- Align generated IDs with the entity identity mapping; invalid legacy IDs fail rather than being deleted.
SET @grantforge_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.table_constraints
            WHERE table_schema = DATABASE() AND table_name = 'system_log_type' AND constraint_type = 'PRIMARY KEY'),
    'SELECT 1', 'ALTER TABLE `system_log_type` ADD PRIMARY KEY (`id`)');
PREPARE grantforge_statement FROM @grantforge_ddl;
EXECUTE grantforge_statement;
DEALLOCATE PREPARE grantforge_statement;
ALTER TABLE `system_log_type` MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT;

-- Align generated IDs with the entity identity mapping; invalid legacy IDs fail rather than being deleted.
SET @grantforge_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.table_constraints
            WHERE table_schema = DATABASE() AND table_name = 'system_menu_type' AND constraint_type = 'PRIMARY KEY'),
    'SELECT 1', 'ALTER TABLE `system_menu_type` ADD PRIMARY KEY (`id`)');
PREPARE grantforge_statement FROM @grantforge_ddl;
EXECUTE grantforge_statement;
DEALLOCATE PREPARE grantforge_statement;
ALTER TABLE `system_menu_type` MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT;
