-- -----------------------------------------------------
-- Migration 008: Announcements per course offering
-- Course announcements used to point only at a course, so they reached that course's students
-- in every term and every teacher of the course saw them. Now each one belongs to a course
-- offering (course + teacher + term). General announcements (no offering) get an audience:
-- everyone, students only or teachers only. Edits are timestamped.
-- Run ONCE against an existing cms_ead database:
--   mysql -u root -p cms_ead < database_migrations/008_announcement_offerings.sql
-- Fresh installs do not need this: database_schema.sql already contains it.
-- -----------------------------------------------------
USE cms_ead;

ALTER TABLE `announcements`
  ADD COLUMN `allocation_id` INT NULL AFTER `course_id`,
  ADD COLUMN `audience` ENUM('ALL', 'STUDENTS', 'TEACHERS') NOT NULL DEFAULT 'ALL' AFTER `allocation_id`,
  ADD COLUMN `updated_at` TIMESTAMP NULL DEFAULT NULL AFTER `created_at`,
  ADD KEY `idx_announcements_allocation` (`allocation_id`),
  ADD CONSTRAINT `fk_announcements_allocation` FOREIGN KEY (`allocation_id`) REFERENCES `course_allocations`(`allocation_id`) ON DELETE CASCADE;

-- Each existing course announcement goes to its author's offering of that course (newest term),
-- or, if the author does not teach it, to the course's newest offering.
UPDATE `announcements` a
SET a.`allocation_id` = (
    SELECT ca.allocation_id
    FROM course_allocations ca
    JOIN semesters s ON s.semester_id = ca.semester_id
    WHERE ca.course_id = a.course_id
    ORDER BY (ca.teacher_id = a.created_by) DESC, s.start_date DESC, ca.allocation_id DESC
    LIMIT 1)
WHERE a.`course_id` IS NOT NULL;
