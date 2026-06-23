-- V43__skill_author_attribution.sql
-- Separate original author from skill manager (uploader), and add source attribution.

ALTER TABLE skill ADD COLUMN author_name VARCHAR(256);
ALTER TABLE skill ADD COLUMN source_platform VARCHAR(128);
ALTER TABLE skill ADD COLUMN source_url VARCHAR(512);
