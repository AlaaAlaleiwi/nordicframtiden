-- 1) Add columns as nullable first (safe for existing rows)
ALTER TABLE user_profile
  ADD COLUMN year_of_birth INTEGER,
  ADD COLUMN county_code VARCHAR(2),
  ADD COLUMN municipality_code VARCHAR(4);

-- 2) Backfill existing rows
-- ✅ IMPORTANT: change these defaults to something valid for your business
-- Example: Stockholm (01) + Stockholm kommun (0180) + 1990
UPDATE user_profile
SET
  year_of_birth = COALESCE(year_of_birth, 1990),
  county_code = COALESCE(county_code, '01'),
  municipality_code = COALESCE(municipality_code, '0180');

-- 3) Enforce NOT NULL
ALTER TABLE user_profile
  ALTER COLUMN year_of_birth SET NOT NULL,
  ALTER COLUMN county_code SET NOT NULL,
  ALTER COLUMN municipality_code SET NOT NULL;

-- 4) Basic format checks
-- county_code must be 2 digits
ALTER TABLE user_profile
  ADD CONSTRAINT chk_user_profile_county_code_2digits
  CHECK (county_code ~ '^[0-9]{2}$');

-- municipality_code must be 4 digits
ALTER TABLE user_profile
  ADD CONSTRAINT chk_user_profile_municipality_code_4digits
  CHECK (municipality_code ~ '^[0-9]{4}$');

-- municipality_code should start with county_code
ALTER TABLE user_profile
  ADD CONSTRAINT chk_user_profile_municipality_matches_county
  CHECK (substring(municipality_code, 1, 2) = county_code);

-- 5) Optional: keep year sane
ALTER TABLE user_profile
  ADD CONSTRAINT chk_user_profile_year_of_birth
  CHECK (year_of_birth BETWEEN 1900 AND EXTRACT(YEAR FROM CURRENT_DATE)::int);