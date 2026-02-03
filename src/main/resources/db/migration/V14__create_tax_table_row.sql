CREATE TABLE IF NOT EXISTS tax_table_row (
  id BIGSERIAL PRIMARY KEY,

  tax_year INT NOT NULL,          -- År
  days_count INT NOT NULL,        -- Antal dgr
  table_number INT NOT NULL,      -- Tabellnr

  income_from INT NOT NULL,       -- Inkomst fr.o.m.
  income_to INT NOT NULL,         -- Inkomst t.o.m.

  col_1 INT NOT NULL,             -- Kolumn 1
  col_2 INT NOT NULL,             -- Kolumn 2
  col_3 INT NOT NULL,             -- Kolumn 3
  col_4 INT NOT NULL,             -- Kolumn 4
  col_5 INT NOT NULL,             -- Kolumn 5
  col_6 INT NOT NULL,             -- Kolumn 6
  col_7 INT NOT NULL              -- Kolumn 7
);

CREATE INDEX IF NOT EXISTS idx_tax_lookup
ON tax_table_row (tax_year, days_count, table_number, income_from, income_to);

CREATE TABLE IF NOT EXISTS municipality_tax_table (
  municipality_code CHAR(4) NOT NULL,
  tax_year INT NOT NULL,
  table_number INT NOT NULL,
  PRIMARY KEY (municipality_code, tax_year)
);

CREATE INDEX IF NOT EXISTS idx_muni_tax
ON municipality_tax_table (tax_year, municipality_code);