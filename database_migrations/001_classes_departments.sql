-- -----------------------------------------------------
-- Migration 001: Classes (students) and Departments (teachers)
-- Run ONCE against an existing cms_ead database:
--   mysql -u root -p cms_ead < database_migrations/001_classes_departments.sql
-- Fresh installs do not need this: database_schema.sql already contains it.
-- -----------------------------------------------------
USE cms_ead;

-- Departments that teachers belong to, e.g. 'Computer Science'
CREATE TABLE `departments` (
  `department_id` INT AUTO_INCREMENT PRIMARY KEY,
  `name` VARCHAR(100) NOT NULL UNIQUE
);

-- Student classes, displayed as "<degree> <program_name>-<batch_year>",
-- e.g. degree 'BS', program 'Computer Science', year 2026 -> "BS Computer Science-2026"
CREATE TABLE `classes` (
  `class_id` INT AUTO_INCREMENT PRIMARY KEY,
  `degree` VARCHAR(20) NOT NULL,
  `program_name` VARCHAR(100) NOT NULL,
  `batch_year` SMALLINT NOT NULL CHECK (`batch_year` BETWEEN 1950 AND 2100),
  UNIQUE KEY `unique_class` (`degree`, `program_name`, `batch_year`)
);

-- A student belongs to one class, a teacher to one department.
-- Deleting a class/department that is still in use is blocked (default RESTRICT).
ALTER TABLE `users`
  ADD COLUMN `class_id` INT NULL AFTER `role`,
  ADD COLUMN `department_id` INT NULL AFTER `class_id`,
  ADD CONSTRAINT `fk_users_class` FOREIGN KEY (`class_id`) REFERENCES `classes`(`class_id`),
  ADD CONSTRAINT `fk_users_department` FOREIGN KEY (`department_id`) REFERENCES `departments`(`department_id`);

-- Who processed an add/drop request and when (shown in the admin history)
ALTER TABLE `course_requests`
  ADD COLUMN `processed_at` TIMESTAMP NULL DEFAULT NULL,
  ADD COLUMN `processed_by` INT NULL,
  ADD CONSTRAINT `fk_requests_processed_by` FOREIGN KEY (`processed_by`) REFERENCES `users`(`user_id`) ON DELETE SET NULL;

-- Seed data matching the sample accounts
INSERT INTO `departments` (`name`) VALUES ('Computer Science');
INSERT INTO `classes` (`degree`, `program_name`, `batch_year`) VALUES ('BS', 'Computer Science', 2022);

UPDATE `users` SET `class_id` = (SELECT `class_id` FROM `classes`
    WHERE `degree` = 'BS' AND `program_name` = 'Computer Science' AND `batch_year` = 2022)
WHERE `username` = 'BCSF22M512';

UPDATE `users` SET `department_id` = (SELECT `department_id` FROM `departments` WHERE `name` = 'Computer Science')
WHERE `username` = 'TEACHER1';
