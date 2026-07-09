-- V2002__skill_description_field.sql
-- Add uploader-provided description field separate from SKILL.md summary.

ALTER TABLE skill ADD COLUMN description TEXT;
