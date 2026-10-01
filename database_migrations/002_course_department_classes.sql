-- -----------------------------------------------------
-- Migration 002: a course belongs to a Department and is offered to one or more Classes
-- Requires migration 001. Run ONCE against an existing cms_ead database:
--   mysql -u root -p cms_ead < database_migrations/002_course_department_classes.sql
-- Fresh installs do not need this: database_schema.sql already contains it.
-- -----------------------------------------------------
USE cms_ead;

-- Owning department (used to list that department's teachers first when assigning)
ALTER TABLE `courses`
  ADD COLUMN `department_id` INT NULL AFTER `credit_hours`,
  ADD CONSTRAINT `fk_courses_department` FOREIGN KEY (`department_id`) REFERENCES `departments`(`department_id`);

-- Classes a course is offered to (used to list those classes' students first when enrolling)
CREATE TABLE `course_classes` (
  `course_id` INT NOT NULL,
  `class_id` INT NOT NULL,
  PRIMARY KEY (`course_id`, `class_id`),
  FOREIGN KEY (`course_id`) REFERENCES `courses`(`course_id`) ON DELETE CASCADE,
  FOREIGN KEY (`class_id`) REFERENCES `classes`(`class_id`) ON DELETE CASCADE
);

-- Seed: the CS courses belong to Computer Science; OOP and DSA are taught to BS Computer Science-2022
UPDATE `courses` SET `department_id` = (SELECT `department_id` FROM `departments` WHERE `name` = 'Computer Science')
WHERE `course_code` LIKE 'CS-%';

INSERT INTO `course_classes` (`course_id`, `class_id`)
SELECT c.`course_id`, cl.`class_id`
FROM `courses` c
JOIN `classes` cl ON cl.`degree` = 'BS' AND cl.`program_name` = 'Computer Science' AND cl.`batch_year` = 2022
WHERE c.`course_code` IN ('CS-201', 'CS-301');
