-- -----------------------------------------------------
-- Migration 003: fee challans get a title, amount, payment date and issuer;
-- the attachment becomes optional (the system generates a printable voucher).
-- Run ONCE against an existing cms_ead database:
--   mysql -u root -p cms_ead < database_migrations/003_challan_details.sql
-- Fresh installs do not need this: database_schema.sql already contains it.
-- -----------------------------------------------------
USE cms_ead;

ALTER TABLE `challans`
  ADD COLUMN `title` VARCHAR(100) NOT NULL DEFAULT 'Fee Challan' AFTER `semester_id`,
  ADD COLUMN `amount` DECIMAL(10,2) NULL AFTER `title`,
  MODIFY COLUMN `file_path` VARCHAR(255) NULL,
  ADD COLUMN `remarks` VARCHAR(255) NULL AFTER `status`,
  ADD COLUMN `paid_at` TIMESTAMP NULL DEFAULT NULL AFTER `remarks`,
  ADD COLUMN `created_by` INT NULL AFTER `paid_at`,
  ADD CONSTRAINT `fk_challans_created_by` FOREIGN KEY (`created_by`) REFERENCES `users`(`user_id`) ON DELETE SET NULL,
  ADD CHECK (`amount` IS NULL OR `amount` >= 0);

-- The seed challans pointed at files that were never shipped; give them real details
UPDATE `challans` SET `title` = 'Semester Fee - Fall 2024', `amount` = 45000.00, `file_path` = NULL,
       `paid_at` = '2024-09-10 10:00:00'
WHERE `file_path` = 'challans/sem_fall_24.pdf';

UPDATE `challans` SET `title` = 'Late Registration Fine', `amount` = 2000.00, `file_path` = NULL
WHERE `file_path` = 'challans/fine_late.pdf';
