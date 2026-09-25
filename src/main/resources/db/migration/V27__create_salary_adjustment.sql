CREATE TABLE salary_adjustment (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  salary_year INT NOT NULL,
  salary_month INT NOT NULL CHECK (salary_month BETWEEN 1 AND 12),
  name VARCHAR(120) NOT NULL,
  amount NUMERIC(12,2) NOT NULL CHECK (amount >= 0),
  tax_treatment VARCHAR(32) NOT NULL CHECK (tax_treatment IN ('REGULAR_TAXABLE','ONE_TIME_TAXABLE','TAX_FREE'))
);
CREATE INDEX idx_salary_adjustment_period ON salary_adjustment(user_id, salary_year, salary_month);
