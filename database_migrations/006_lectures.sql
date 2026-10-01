-- -----------------------------------------------------
-- Migration 006: Lectures
-- Attendance used to store its own lecture number and date on every student's row, so the
-- students of one lecture could disagree on its date and numbers had to be typed by hand.
-- Now each lecture held is one row in `lectures` (course offering + date), and attendance
-- rows point to it. Lecture numbers are not stored: they follow date order.
-- Run ONCE against an existing cms_ead database:
--   mysql -u root -p cms_ead < database_migrations/006_lectures.sql
-- Fresh installs do not need this: database_schema.sql already contains it.
-- -----------------------------------------------------
USE cms_ead;

CREATE TABLE `lectures` (
  `lecture_id` INT AUTO_INCREMENT PRIMARY KEY,
  `allocation_id` INT NOT NULL,                 -- the course offering (course + teacher + term)
  `lecture_date` DATE NOT NULL,
  `topic` VARCHAR(200) NULL,
  `created_by` INT NULL,
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  `updated_by` INT NULL,
  `updated_at` TIMESTAMP NULL DEFAULT NULL,
  `legacy_number` INT NULL,                     -- used only by this migration, dropped below
  KEY `idx_lectures_allocation_date` (`allocation_id`, `lecture_date`),
  CONSTRAINT `fk_lectures_allocation` FOREIGN KEY (`allocation_id`) REFERENCES `course_allocations`(`allocation_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_lectures_created_by` FOREIGN KEY (`created_by`) REFERENCES `users`(`user_id`) ON DELETE SET NULL,
  CONSTRAINT `fk_lectures_updated_by` FOREIGN KEY (`updated_by`) REFERENCES `users`(`user_id`) ON DELETE SET NULL
);

-- One lecture per old (course offering, lecture number), dated by the earliest date recorded for it
INSERT INTO `lectures` (`allocation_id`, `lecture_date`, `created_by`, `created_at`, `legacy_number`)
SELECT e.allocation_id, MIN(a.date), ca.teacher_id, MIN(a.date), a.lecture_number
FROM attendance a
JOIN enrollments e ON e.enrollment_id = a.enrollment_id
JOIN course_allocations ca ON ca.allocation_id = e.allocation_id
GROUP BY e.allocation_id, a.lecture_number, ca.teacher_id;

ALTER TABLE `attendance`
  ADD COLUMN `lecture_id` INT NULL AFTER `attendance_id`,
  ADD KEY `idx_attendance_enrollment` (`enrollment_id`); -- keeps the enrollment foreign key indexed

UPDATE attendance a
JOIN enrollments e ON e.enrollment_id = a.enrollment_id
JOIN lectures l ON l.allocation_id = e.allocation_id AND l.legacy_number = a.lecture_number
SET a.lecture_id = l.lecture_id;

ALTER TABLE `attendance`
  DROP INDEX `unique_attendance`,
  DROP CHECK `attendance_chk_1`,
  DROP COLUMN `lecture_number`,
  DROP COLUMN `date`,
  MODIFY `lecture_id` INT NOT NULL,
  ADD UNIQUE KEY `unique_lecture_student` (`lecture_id`, `enrollment_id`),
  ADD CONSTRAINT `fk_attendance_lecture` FOREIGN KEY (`lecture_id`) REFERENCES `lectures`(`lecture_id`) ON DELETE CASCADE;

ALTER TABLE `lectures` DROP COLUMN `legacy_number`;
