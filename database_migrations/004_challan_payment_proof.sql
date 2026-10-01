-- -----------------------------------------------------
-- Migration 004: students can upload proof of payment for a challan; the admin
-- accepts it (challan becomes PAID) or rejects it with a reason.
-- Requires migration 003. Run ONCE against an existing cms_ead database:
--   mysql -u root -p cms_ead < database_migrations/004_challan_payment_proof.sql
-- Fresh installs do not need this: database_schema.sql already contains it.
-- -----------------------------------------------------
USE cms_ead;

ALTER TABLE `challans`
  ADD COLUMN `proof_path` VARCHAR(255) NULL AFTER `created_by`,          -- uploaded receipt (stored outside the web app)
  ADD COLUMN `proof_reference` VARCHAR(100) NULL AFTER `proof_path`,     -- bank transaction / reference no. given by the student
  ADD COLUMN `proof_status` ENUM('NONE', 'SUBMITTED', 'ACCEPTED', 'REJECTED') NOT NULL DEFAULT 'NONE' AFTER `proof_reference`,
  ADD COLUMN `proof_submitted_at` TIMESTAMP NULL DEFAULT NULL AFTER `proof_status`,
  ADD COLUMN `proof_review_note` VARCHAR(255) NULL AFTER `proof_submitted_at`, -- reason when rejected
  ADD COLUMN `proof_reviewed_at` TIMESTAMP NULL DEFAULT NULL AFTER `proof_review_note`;
