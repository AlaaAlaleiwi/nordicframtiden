CREATE TABLE one_time_tax_rate (
  id BIGSERIAL PRIMARY KEY,
  tax_year INT NOT NULL,
  tax_column INT NOT NULL CHECK (tax_column BETWEEN 1 AND 6),
  annual_income_from INT NOT NULL,
  annual_income_to INT NOT NULL,
  tax_percent INT NOT NULL CHECK (tax_percent BETWEEN 0 AND 100),
  UNIQUE (tax_year, tax_column, annual_income_from)
);
CREATE INDEX idx_one_time_tax_lookup
ON one_time_tax_rate(tax_year, tax_column, annual_income_from, annual_income_to);
