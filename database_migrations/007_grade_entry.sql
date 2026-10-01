-- -----------------------------------------------------
-- Migration 007: Grade entry
-- Marks used to default to 0, so "not entered yet" (e.g. the final exam has not been held)
-- looked like a real zero: the grade was worked out from partial marks (an F) and could reach
-- the transcript. Now a mark is NULL until it is entered, the grade is set only once all three
-- marks are entered, marks cannot be negative, and each row records who changed it last.
-- Run ONCE against an existing cms_ead database:
--   mysql -u root -p cms_ead < database_migrations/007_grade_entry.sql
-- Fresh installs do not need this: database_schema.sql already contains it.
-- -----------------------------------------------------
USE cms_ead;

ALTER TABLE `grades`
  DROP CHECK `grades_chk_1`,
  DROP CHECK `grades_chk_2`,
  DROP CHECK `grades_chk_3`;

ALTER TABLE `grades`
  MODIFY `sessional_marks` DOUBLE NULL DEFAULT NULL,
  MODIFY `mid_marks` DOUBLE NULL DEFAULT NULL,
  MODIFY `final_marks` DOUBLE NULL DEFAULT NULL,
  ADD COLUMN `updated_by` INT NULL AFTER `is_published`,
  ADD COLUMN `updated_at` TIMESTAMP NULL DEFAULT NULL AFTER `updated_by`,
  ADD CONSTRAINT `chk_grades_sessional` CHECK (`sessional_marks` BETWEEN 0 AND 25),
  ADD CONSTRAINT `chk_grades_mid` CHECK (`mid_marks` BETWEEN 0 AND 35),
  ADD CONSTRAINT `chk_grades_final` CHECK (`final_marks` BETWEEN 0 AND 40),
  ADD CONSTRAINT `fk_grades_updated_by` FOREIGN KEY (`updated_by`) REFERENCES `users`(`user_id`) ON DELETE SET NULL;

-- Rows a teacher never saved (no grade letter yet) still hold the old placeholder zeros:
-- treat those zeros as "not entered". Rows a teacher saved are left as they are, because a
-- zero there may be a real mark.
UPDATE `grades` SET `sessional_marks` = NULL WHERE `grade_letter` IS NULL AND `sessional_marks` = 0;
UPDATE `grades` SET `mid_marks` = NULL WHERE `grade_letter` IS NULL AND `mid_marks` = 0;
UPDATE `grades` SET `final_marks` = NULL WHERE `grade_letter` IS NULL AND `final_marks` = 0;
