ALTER TABLE tax_table_row DROP COLUMN IF EXISTS days_count;
ALTER TABLE tax_table_row DROP COLUMN IF EXISTS col_7;
ALTER TABLE tax_table_row ADD COLUMN IF NOT EXISTS percentage BOOLEAN NOT NULL DEFAULT FALSE;

DROP INDEX IF EXISTS idx_tax_lookup;
CREATE INDEX IF NOT EXISTS idx_tax_lookup
ON tax_table_row (tax_year, table_number, income_from, income_to);
