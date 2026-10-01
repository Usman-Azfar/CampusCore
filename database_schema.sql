-- =====================================================
-- CampusCore - A Smart Campus Management System
-- Database schema and sample data (fresh install).
-- Existing databases: run database_migrations/*.sql in order instead.
-- =====================================================

-- Database Creation
CREATE DATABASE IF NOT EXISTS cms_ead;
USE cms_ead;

-- Disable Detail Checks for Bulk Import
SET FOREIGN_KEY_CHECKS = 0;

-- -----------------------------------------------------
-- Table: departments (Teachers belong to one)
-- -----------------------------------------------------
DROP TABLE IF EXISTS `departments`;
CREATE TABLE `departments` (
  `department_id` INT AUTO_INCREMENT PRIMARY KEY,
  `name` VARCHAR(100) NOT NULL UNIQUE -- e.g. 'Computer Science'
);

-- -----------------------------------------------------
-- Table: classes (Students belong to one)
-- Displayed as "<degree> <program_name>-<batch_year>", e.g. "BS Computer Science-2026"
-- -----------------------------------------------------
DROP TABLE IF EXISTS `classes`;
CREATE TABLE `classes` (
  `class_id` INT AUTO_INCREMENT PRIMARY KEY,
  `degree` VARCHAR(20) NOT NULL,          -- e.g. 'BS'
  `program_name` VARCHAR(100) NOT NULL,   -- e.g. 'Computer Science'
  `batch_year` SMALLINT NOT NULL CHECK (`batch_year` BETWEEN 1950 AND 2100), -- e.g. 2026
  `department_id` INT NULL,               -- Optional owning department
  UNIQUE KEY `unique_class` (`degree`, `program_name`, `batch_year`),
  CONSTRAINT `fk_classes_department` FOREIGN KEY (`department_id`) REFERENCES `departments`(`department_id`)
);

-- -----------------------------------------------------
-- Table: users
-- Roles: 'ADMIN', 'TEACHER', 'STUDENT'
-- -----------------------------------------------------
DROP TABLE IF EXISTS `users`;
CREATE TABLE `users` (
  `user_id` INT AUTO_INCREMENT PRIMARY KEY,
  `username` VARCHAR(50) NOT NULL UNIQUE, -- Roll Number or Employee ID
  `password` VARCHAR(255) NOT NULL,
  `role` ENUM('ADMIN', 'TEACHER', 'STUDENT') NOT NULL,
  `class_id` INT NULL,       -- Students only
  `department_id` INT NULL,  -- Teachers only
  `is_active` BOOLEAN DEFAULT TRUE,
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT `fk_users_class` FOREIGN KEY (`class_id`) REFERENCES `classes`(`class_id`),
  CONSTRAINT `fk_users_department` FOREIGN KEY (`department_id`) REFERENCES `departments`(`department_id`)
);

-- -----------------------------------------------------
-- Table: profiles
-- Stores personal information for all users
-- -----------------------------------------------------
DROP TABLE IF EXISTS `profiles`;
CREATE TABLE `profiles` (
  `profile_id` INT AUTO_INCREMENT PRIMARY KEY,
  `user_id` INT NOT NULL,
  `full_name` VARCHAR(100) NOT NULL,
  `gender` ENUM('Male', 'Female', 'Other'),
  `father_name` VARCHAR(100),
  `phone` VARCHAR(20),
  `address` TEXT,
  `city` VARCHAR(50),
  `country` VARCHAR(50),
  `email` VARCHAR(100) UNIQUE,
  `img_data` LONGBLOB, -- Profile picture
  FOREIGN KEY (`user_id`) REFERENCES `users`(`user_id`) ON DELETE CASCADE
);

-- -----------------------------------------------------
-- Table: semesters (terms, e.g. 'Fall 2024')
-- is_active: TRUE while at least one class is currently in this term. Several terms can be
-- active at once. The application keeps it up to date from class_semesters.
-- -----------------------------------------------------
DROP TABLE IF EXISTS `semesters`;
CREATE TABLE `semesters` (
  `semester_id` INT AUTO_INCREMENT PRIMARY KEY,
  `name` VARCHAR(50) NOT NULL, -- e.g., 'Fall 2024', 'Spring 2025'
  `start_date` DATE,
  `end_date` DATE,
  `is_active` BOOLEAN DEFAULT FALSE
);

-- -----------------------------------------------------
-- Table: class_semesters
-- The semester number a class is in during a term, e.g. in Fall 2024
-- "BS Computer Science-2022" is in its 5th semester and "BS Computer Science-2025" in its 1st.
-- A class has at most one current term (one_current_per_class).
-- -----------------------------------------------------
DROP TABLE IF EXISTS `class_semesters`;
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

-- -----------------------------------------------------
-- Table: courses
-- -----------------------------------------------------
DROP TABLE IF EXISTS `courses`;
CREATE TABLE `courses` (
  `course_id` INT AUTO_INCREMENT PRIMARY KEY,
  `course_code` VARCHAR(20) NOT NULL UNIQUE,
  `course_name` VARCHAR(100) NOT NULL,
  `credit_hours` INT DEFAULT 3,
  `department_id` INT NULL, -- Owning department
  `description` TEXT,
  CONSTRAINT `fk_courses_department` FOREIGN KEY (`department_id`) REFERENCES `departments`(`department_id`)
);

-- -----------------------------------------------------
-- Table: course_classes (Classes a course is offered to)
-- -----------------------------------------------------
DROP TABLE IF EXISTS `course_classes`;
CREATE TABLE `course_classes` (
  `course_id` INT NOT NULL,
  `class_id` INT NOT NULL,
  PRIMARY KEY (`course_id`, `class_id`),
  FOREIGN KEY (`course_id`) REFERENCES `courses`(`course_id`) ON DELETE CASCADE,
  FOREIGN KEY (`class_id`) REFERENCES `classes`(`class_id`) ON DELETE CASCADE
);

-- -----------------------------------------------------
-- Table: course_allocations (Course offerings per semester)
-- Links a course to a teacher for a specific semester
-- -----------------------------------------------------
DROP TABLE IF EXISTS `course_allocations`;
CREATE TABLE `course_allocations` (
  `allocation_id` INT AUTO_INCREMENT PRIMARY KEY,
  `course_id` INT NOT NULL,
  `teacher_id` INT NOT NULL, -- References users(user_id) where role='TEACHER'
  `semester_id` INT NOT NULL,
  UNIQUE(`course_id`, `semester_id`),
  FOREIGN KEY (`course_id`) REFERENCES `courses`(`course_id`),
  FOREIGN KEY (`teacher_id`) REFERENCES `users`(`user_id`),
  FOREIGN KEY (`semester_id`) REFERENCES `semesters`(`semester_id`)
);

-- -----------------------------------------------------
-- Table: enrollments
-- Links student to a course allocation
-- -----------------------------------------------------
DROP TABLE IF EXISTS `enrollments`;
CREATE TABLE `enrollments` (
  `enrollment_id` INT AUTO_INCREMENT PRIMARY KEY,
  `student_id` INT NOT NULL, -- References users(user_id) where role='STUDENT'
  `allocation_id` INT NOT NULL,
  `status` ENUM('ENROLLED', 'DROPPED', 'WITHDRAWN') DEFAULT 'ENROLLED',
  `semester_number` INT, -- e.g. 1st, 2nd, ... 8th Semester
  FOREIGN KEY (`student_id`) REFERENCES `users`(`user_id`),
  FOREIGN KEY (`allocation_id`) REFERENCES `course_allocations`(`allocation_id`)
);

-- -----------------------------------------------------
-- Table: grades
-- Marks distribution: Sessional(25), Mid(35), Final(40), in steps of 0.5.
-- A mark is NULL until it is entered (not zero). grade_letter is set by the application
-- only once all three marks are entered. Students see a result only when is_published;
-- the transcript counts published results with all three marks.
-- -----------------------------------------------------
DROP TABLE IF EXISTS `grades`;
CREATE TABLE `grades` (
  `grade_id` INT AUTO_INCREMENT PRIMARY KEY,
  `enrollment_id` INT NOT NULL UNIQUE,
  `sessional_marks` DOUBLE NULL DEFAULT NULL,
  `mid_marks` DOUBLE NULL DEFAULT NULL,
  `final_marks` DOUBLE NULL DEFAULT NULL,
  `total_marks` DOUBLE GENERATED ALWAYS AS (`sessional_marks` + `mid_marks` + `final_marks`) STORED, -- NULL until complete
  `grade_letter` VARCHAR(2), -- A, B+, B, etc.
  `is_published` BOOLEAN DEFAULT FALSE,
  `updated_by` INT NULL,
  `updated_at` TIMESTAMP NULL DEFAULT NULL,
  CONSTRAINT `chk_grades_sessional` CHECK (`sessional_marks` BETWEEN 0 AND 25),
  CONSTRAINT `chk_grades_mid` CHECK (`mid_marks` BETWEEN 0 AND 35),
  CONSTRAINT `chk_grades_final` CHECK (`final_marks` BETWEEN 0 AND 40),
  FOREIGN KEY (`enrollment_id`) REFERENCES `enrollments`(`enrollment_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_grades_updated_by` FOREIGN KEY (`updated_by`) REFERENCES `users`(`user_id`) ON DELETE SET NULL
);

-- -----------------------------------------------------
-- Table: lectures
-- One row per lecture held in a course offering. Lecture numbers are not stored:
-- they follow date order. Up to 32 lectures per offering (enforced by the application).
-- -----------------------------------------------------
DROP TABLE IF EXISTS `lectures`;
CREATE TABLE `lectures` (
  `lecture_id` INT AUTO_INCREMENT PRIMARY KEY,
  `allocation_id` INT NOT NULL,                 -- the course offering (course + teacher + term)
  `lecture_date` DATE NOT NULL,
  `topic` VARCHAR(200) NULL,
  `created_by` INT NULL,
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  `updated_by` INT NULL,
  `updated_at` TIMESTAMP NULL DEFAULT NULL,
  KEY `idx_lectures_allocation_date` (`allocation_id`, `lecture_date`),
  CONSTRAINT `fk_lectures_allocation` FOREIGN KEY (`allocation_id`) REFERENCES `course_allocations`(`allocation_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_lectures_created_by` FOREIGN KEY (`created_by`) REFERENCES `users`(`user_id`) ON DELETE SET NULL,
  CONSTRAINT `fk_lectures_updated_by` FOREIGN KEY (`updated_by`) REFERENCES `users`(`user_id`) ON DELETE SET NULL
);

-- -----------------------------------------------------
-- Table: attendance (one student's status in one lecture)
-- -----------------------------------------------------
DROP TABLE IF EXISTS `attendance`;
CREATE TABLE `attendance` (
  `attendance_id` INT AUTO_INCREMENT PRIMARY KEY,
  `lecture_id` INT NOT NULL,
  `enrollment_id` INT NOT NULL,
  `status` ENUM('Present', 'Absent', 'Leave') NOT NULL,
  UNIQUE KEY `unique_lecture_student` (`lecture_id`, `enrollment_id`),
  KEY `idx_attendance_enrollment` (`enrollment_id`),
  CONSTRAINT `fk_attendance_lecture` FOREIGN KEY (`lecture_id`) REFERENCES `lectures`(`lecture_id`) ON DELETE CASCADE,
  FOREIGN KEY (`enrollment_id`) REFERENCES `enrollments`(`enrollment_id`) ON DELETE CASCADE
);

-- -----------------------------------------------------
-- Table: announcements
-- General (allocation_id and course_id NULL): posted by the admin, seen by `audience`
-- (everyone, students only or teachers only).
-- Course announcements: posted by the teacher of one course offering (allocation_id) and seen by
-- the students currently enrolled in it. course_id is kept in step with the offering's course.
-- -----------------------------------------------------
DROP TABLE IF EXISTS `announcements`;
CREATE TABLE `announcements` (
  `announcement_id` INT AUTO_INCREMENT PRIMARY KEY,
  `title` VARCHAR(100) NOT NULL,
  `content` TEXT NOT NULL,
  `course_id` INT DEFAULT NULL, -- If NULL, it's a general announcement
  `allocation_id` INT NULL,     -- the course offering (course + teacher + term)
  `audience` ENUM('ALL', 'STUDENTS', 'TEACHERS') NOT NULL DEFAULT 'ALL', -- general announcements
  `created_by` INT NOT NULL, -- Teacher or Admin
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NULL DEFAULT NULL,
  KEY `idx_announcements_allocation` (`allocation_id`),
  FOREIGN KEY (`course_id`) REFERENCES `courses`(`course_id`),
  CONSTRAINT `fk_announcements_allocation` FOREIGN KEY (`allocation_id`) REFERENCES `course_allocations`(`allocation_id`) ON DELETE CASCADE,
  FOREIGN KEY (`created_by`) REFERENCES `users`(`user_id`)
);

-- -----------------------------------------------------
-- Table: messages
-- Internal messaging system
-- -----------------------------------------------------
DROP TABLE IF EXISTS `messages`;
CREATE TABLE `messages` (
  `message_id` INT AUTO_INCREMENT PRIMARY KEY,
  `sender_id` INT NOT NULL,
  `receiver_id` INT NOT NULL,
  `model_message` TEXT NOT NULL, -- 'content' might be keyword
  `timestamp` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  `is_read` BOOLEAN DEFAULT FALSE,
  FOREIGN KEY (`sender_id`) REFERENCES `users`(`user_id`),
  FOREIGN KEY (`receiver_id`) REFERENCES `users`(`user_id`)
);

-- -----------------------------------------------------
-- Table: support_tickets (Help Desk)
-- -----------------------------------------------------
DROP TABLE IF EXISTS `support_tickets`;
CREATE TABLE `support_tickets` (
  `ticket_id` INT AUTO_INCREMENT PRIMARY KEY,
  `user_id` INT NOT NULL,
  `query_text` TEXT NOT NULL,
  `admin_reply` TEXT,
  `status` ENUM('OPEN', 'CLOSED') DEFAULT 'OPEN',
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (`user_id`) REFERENCES `users`(`user_id`)
);

-- -----------------------------------------------------
-- Table: course_requests
-- For Add/Drop/Withdraw Subjects
-- -----------------------------------------------------
DROP TABLE IF EXISTS `course_requests`;
CREATE TABLE `course_requests` (
  `request_id` INT AUTO_INCREMENT PRIMARY KEY,
  `student_id` INT NOT NULL,
  `course_id` INT NOT NULL,
  `type` ENUM('ADD', 'DROP', 'WITHDRAW') NOT NULL,
  `status` ENUM('PENDING', 'APPROVED', 'REJECTED') DEFAULT 'PENDING',
  `request_date` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  `processed_at` TIMESTAMP NULL DEFAULT NULL, -- When an admin approved/rejected it
  `processed_by` INT NULL,                    -- Which admin
  FOREIGN KEY (`student_id`) REFERENCES `users`(`user_id`),
  FOREIGN KEY (`course_id`) REFERENCES `courses`(`course_id`),
  CONSTRAINT `fk_requests_processed_by` FOREIGN KEY (`processed_by`) REFERENCES `users`(`user_id`) ON DELETE SET NULL
);

-- -----------------------------------------------------
-- Table: challans
-- -----------------------------------------------------
DROP TABLE IF EXISTS `challans`;
CREATE TABLE `challans` (
  `challan_id` INT AUTO_INCREMENT PRIMARY KEY,
  `student_id` INT NOT NULL,
  `semester_id` INT NOT NULL,
  `title` VARCHAR(100) NOT NULL DEFAULT 'Fee Challan', -- e.g. 'Semester Fee - Fall 2024'
  `amount` DECIMAL(10,2) NULL CHECK (`amount` IS NULL OR `amount` >= 0), -- PKR
  `file_path` VARCHAR(255) NULL, -- Optional attachment, stored outside the web app
  `upload_date` TIMESTAMP DEFAULT CURRENT_TIMESTAMP, -- Issued on
  `due_date` DATE,
  `status` ENUM('UNPAID', 'PAID') DEFAULT 'UNPAID',
  `remarks` VARCHAR(255) NULL,
  `paid_at` TIMESTAMP NULL DEFAULT NULL,
  `created_by` INT NULL, -- Issuing admin
  `proof_path` VARCHAR(255) NULL,          -- Student's proof of payment (stored outside the web app)
  `proof_reference` VARCHAR(100) NULL,     -- Bank transaction / reference no.
  `proof_status` ENUM('NONE', 'SUBMITTED', 'ACCEPTED', 'REJECTED') NOT NULL DEFAULT 'NONE',
  `proof_submitted_at` TIMESTAMP NULL DEFAULT NULL,
  `proof_review_note` VARCHAR(255) NULL,   -- Reason when rejected
  `proof_reviewed_at` TIMESTAMP NULL DEFAULT NULL,
  FOREIGN KEY (`student_id`) REFERENCES `users`(`user_id`),
  FOREIGN KEY (`semester_id`) REFERENCES `semesters`(`semester_id`),
  CONSTRAINT `fk_challans_created_by` FOREIGN KEY (`created_by`) REFERENCES `users`(`user_id`) ON DELETE SET NULL
);

-- -----------------------------------------------------
-- Insert Seed Data (Department and Class)
-- -----------------------------------------------------
INSERT INTO `departments` (`name`) VALUES ('Computer Science');
INSERT INTO `classes` (`degree`, `program_name`, `batch_year`, `department_id`) VALUES ('BS', 'Computer Science', 2022, 1);

-- -----------------------------------------------------
-- Insert Seed Data (sample accounts)
-- Passwords are stored as PBKDF2 hashes. Plain values: ADMIN123, Usman123, Teacher123.
-- To make a new hash: java -cp target/classes com.cms.util.PasswordHasher <password>
-- -----------------------------------------------------
INSERT INTO `users` (`username`, `password`, `role`) VALUES ('ADMIN', 'pbkdf2_sha256$600000$EiUnsaoh8n1j8ES9xnHoow==$nmFKnO8BFaQAPSWrSd1xEMMgD8fEEQwzhe/+4UBSTfE=', 'ADMIN');
INSERT INTO `users` (`username`, `password`, `role`, `class_id`) VALUES ('BCSF22M512', 'pbkdf2_sha256$600000$hR3qIvAeUDoaJaSAds5gZw==$odNmOuk93/nYC05Jx1oOKEYa319ji55MFznTIJ1kAEM=', 'STUDENT', 1);

INSERT INTO `users` (`username`, `password`, `role`, `department_id`) VALUES ('TEACHER1', 'pbkdf2_sha256$600000$UbRW9R+Iz5YJzwDMfXQ2Lw==$/o7DimjeYC/sQL7t5VXeCCamHAeB/FgjucdRwe+lnMc=', 'TEACHER', 1);

-- Insert Seed Profiles (Required for logic relying on profiles)
INSERT INTO `profiles` (`user_id`, `full_name`, `gender`, `email`, `city`, `country`) 
VALUES (1, 'System Administrator', 'Male', 'admin@cms.edu.pk', 'Lahore', 'Pakistan');

INSERT INTO `profiles` (`user_id`, `full_name`, `gender`, `email`, `city`, `country`) 
VALUES (2, 'Usman', 'Male', 'bcsf22m512@campuscore.edu.pk', 'Lahore', 'Pakistan');

INSERT INTO `profiles` (`user_id`, `full_name`, `gender`, `email`, `city`, `country`) 
VALUES (3, 'Dr. Sarah Ahmed', 'Female', 'sarah@campuscore.edu.pk', 'Lahore', 'Pakistan');


-- -----------------------------------------------------
-- Insert Seed Data (Term and the class's current semester)
-- -----------------------------------------------------
INSERT INTO `semesters` (`name`, `start_date`, `end_date`, `is_active`)
VALUES ('Fall 2024', '2024-09-01', '2025-01-15', TRUE);

-- BS Computer Science-2022 is in its 3rd semester in Fall 2024 (its current term)
INSERT INTO `class_semesters` (`class_id`, `semester_id`, `semester_number`, `is_current`) VALUES (1, 1, 3, TRUE);

-- -----------------------------------------------------
-- Insert Seed Data (Courses)
-- -----------------------------------------------------
INSERT INTO `courses` (`course_code`, `course_name`, `credit_hours`, `department_id`, `description`) VALUES
('CS-101', 'Introduction to ICT', 3, 1, 'Basics of Information Communication Technology'),
('CS-102', 'Programming Fundamentals', 4, 1, 'Introduction to C++ and Problem Solving'),
('CS-201', 'Object Oriented Programming', 4, 1, 'Java and OOP Concepts'),
('CS-301', 'Data Structures & Algorithms', 4, 1, 'Advanced storage and retrieval algorithms'),
('MATH-101', 'Calculus I', 3, NULL, 'Limits, Derivatives and Integrals');

-- OOP and DSA are taught to BS Computer Science-2022
INSERT INTO `course_classes` (`course_id`, `class_id`) VALUES (3, 1), (4, 1);

-- -----------------------------------------------------
-- Insert Seed Data (Course Allocations)
-- Assign courses to 'TEACHER1' (User ID 3) for Semester 1 (Fall 2024)
-- -----------------------------------------------------
INSERT INTO `course_allocations` (`course_id`, `teacher_id`, `semester_id`) VALUES 
(3, 3, 1), -- OOP assigned to Dr. Sarah
(4, 3, 1); -- DSA assigned to Dr. Sarah

-- -----------------------------------------------------
-- Insert Seed Data (Enrollments)
-- Enroll Student 'BCSF22M512' (User ID 2) in OOP and DSA
-- -----------------------------------------------------
INSERT INTO `enrollments` (`student_id`, `allocation_id`, `semester_number`) VALUES 
(2, 1, 3), -- Enrolled in OOP
(2, 2, 3); -- Enrolled in DSA

-- -----------------------------------------------------
-- Insert Seed Data (Grades)
-- -----------------------------------------------------
INSERT INTO `grades` (`enrollment_id`, `sessional_marks`, `mid_marks`, `final_marks`, `grade_letter`, `is_published`, `updated_by`, `updated_at`) VALUES 
(1, 20, 28, 35, 'A-', TRUE, 3, '2025-01-20 10:00:00'),    -- OOP: complete and published (total 83)
(2, 18, 25, NULL, NULL, FALSE, 3, '2024-11-15 10:00:00'); -- DSA: final exam not held yet

-- -----------------------------------------------------
-- Insert Seed Data (Lectures and attendance)
-- Five OOP lectures (allocation 1, taught by TEACHER1 = user 3) for enrollment 1
-- -----------------------------------------------------
INSERT INTO `lectures` (`allocation_id`, `lecture_date`, `created_by`, `created_at`) VALUES
(1, '2024-09-02', 3, '2024-09-02 10:00:00'),
(1, '2024-09-04', 3, '2024-09-04 10:00:00'),
(1, '2024-09-09', 3, '2024-09-09 10:00:00'),
(1, '2024-09-11', 3, '2024-09-11 10:00:00'),
(1, '2024-09-16', 3, '2024-09-16 10:00:00');

INSERT INTO `attendance` (`lecture_id`, `enrollment_id`, `status`) VALUES
(1, 1, 'Present'),
(2, 1, 'Present'),
(3, 1, 'Absent'),
(4, 1, 'Present'),
(5, 1, 'Leave');

-- -----------------------------------------------------
-- Insert Seed Data (Announcements)
-- -----------------------------------------------------
INSERT INTO `announcements` (`title`, `content`, `course_id`, `allocation_id`, `audience`, `created_by`) VALUES 
('Welcome to Fall 2024', 'Welcome back students! Classes commence from Sep 1st.', NULL, NULL, 'STUDENTS', 1), -- General, by Admin
('OOP Quiz 1', 'Quiz 1 will be held on Monday covering Chapter 1 & 2.', 3, 1, 'ALL', 3);   -- OOP offering (allocation 1), by TEACHER1

-- -----------------------------------------------------
-- Insert Seed Data (Messages)
-- -----------------------------------------------------
INSERT INTO `messages` (`sender_id`, `receiver_id`, `model_message`) VALUES 
(3, 2, 'Please submit your assignment by Friday.'), -- Teacher to Student
(2, 3, 'Maam, I will submit it on time. Thanks for the reminder.'); -- Student to Teacher

-- -----------------------------------------------------
-- Insert Seed Data (Support Tickets)
-- -----------------------------------------------------
INSERT INTO `support_tickets` (`user_id`, `query_text`, `status`, `admin_reply`) VALUES 
(2, 'I cannot see my result for Programming Fundamentals.', 'CLOSED', 'The result has been updated. Please check again.'),
(2, 'Request for Transcript issuance.', 'OPEN', NULL);

-- -----------------------------------------------------
-- Insert Seed Data (Challans)
-- -----------------------------------------------------
INSERT INTO `challans` (`student_id`, `semester_id`, `title`, `amount`, `due_date`, `status`, `paid_at`, `created_by`) VALUES
(2, 1, 'Semester Fee - Fall 2024', 45000.00, '2024-09-15', 'PAID', '2024-09-10 10:00:00', 1),
(2, 1, 'Late Registration Fine', 2000.00, '2024-10-01', 'UNPAID', NULL, 1);

-- -----------------------------------------------------
-- Insert Seed Data (Course Requests)
-- -----------------------------------------------------
INSERT INTO `course_requests` (`student_id`, `course_id`, `type`, `status`) VALUES 
(2, 5, 'ADD', 'PENDING'); -- Request to Add Calculus I

-- Re-enable checks
SET FOREIGN_KEY_CHECKS = 1;

