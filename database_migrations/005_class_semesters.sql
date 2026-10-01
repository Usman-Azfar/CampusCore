-- -----------------------------------------------------
-- Migration 005: Per-class semesters
-- Each class (batch) has its own semester number in each term, e.g. in Fall 2024
-- "BS Computer Science-2022" is in its 5th semester and "BS Computer Science-2025" in its 1st.
-- Several terms can be active at once: a term is active while any class is currently in it.
-- Classes also get an optional department (for filtering, e.g. all 7th-semester classes).
-- Run ONCE against an existing cms_ead database:
--   mysql -u root -p cms_ead < database_migrations/005_class_semesters.sql
-- Fresh installs do not need this: database_schema.sql already contains it.
-- -----------------------------------------------------
USE cms_ead;

-- Optional department a class belongs to
ALTER TABLE `classes`
  ADD COLUMN `department_id` INT NULL AFTER `batch_year`,
  ADD CONSTRAINT `fk_classes_department` FOREIGN KEY (`department_id`) REFERENCES `departments`(`department_id`);

-- Which semester number a class is in during a term, and whether it is the class's current one
CREATE TABLE `class_semesters` (
  `class_semester_id` INT AUTO_INCREMENT PRIMARY KEY,
  `class_id` INT NOT NULL,
  `semester_id` INT NOT NULL,
  `semester_number` TINYINT NOT NULL CHECK (`semester_number` >= 1),
  `is_current` BOOLEAN NOT NULL DEFAULT FALSE,
  -- Equals class_id only while current, so the unique key allows one current term per class
  `current_class_id` INT AS (IF(`is_current`, `class_id`, NULL)) STORED,
  UNIQUE KEY `unique_class_term` (`class_id`, `semester_id`),
  UNIQUE KEY `unique_class_number` (`class_id`, `semester_number`),
  UNIQUE KEY `one_current_per_class` (`current_class_id`),
  -- No ON DELETE CASCADE: MySQL does not allow it on a column a stored generated column uses.
  -- Deleting a class removes its rows first (AcademicDAO.deleteClass).
  CONSTRAINT `fk_class_semesters_class` FOREIGN KEY (`class_id`) REFERENCES `classes`(`class_id`),
  CONSTRAINT `fk_class_semesters_semester` FOREIGN KEY (`semester_id`) REFERENCES `semesters`(`semester_id`)
);

-- Rebuild history from existing enrollments: the semester number the class's students had in
-- each term. The term that was active becomes the class's current semester.
INSERT IGNORE INTO `class_semesters` (`class_id`, `semester_id`, `semester_number`, `is_current`)
SELECT u.class_id, ca.semester_id, MAX(e.semester_number), MAX(s.is_active)
FROM enrollments e
JOIN users u ON u.user_id = e.student_id
JOIN course_allocations ca ON ca.allocation_id = e.allocation_id
JOIN semesters s ON s.semester_id = ca.semester_id
WHERE u.class_id IS NOT NULL AND e.semester_number IS NOT NULL
GROUP BY u.class_id, ca.semester_id;

-- semesters.is_active now means "at least one class is currently in this term".
-- The application keeps it up to date whenever class semesters change.
UPDATE `semesters` s SET s.is_active = EXISTS (
  SELECT 1 FROM class_semesters cs WHERE cs.semester_id = s.semester_id AND cs.is_current = TRUE);
