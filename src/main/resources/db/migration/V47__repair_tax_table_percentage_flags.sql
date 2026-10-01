-- Repairs monthly tax-table rows that were imported before V26 added the
-- percentage column. Above 80 000 kr per month Skatteverket's monthly tables
-- are percentage tables, but those rows kept percentage = FALSE, so the raw
-- percentage (for example 45) was used as the withholding amount in kronor.

-- Bracket rows that can never match a lookup: the old CSV importers wrote 0
-- for the open-ended top bracket instead of an open range.
DELETE FROM tax_table_row WHERE income_to = 0;

-- Restore the percentage flag for every high-income row.
UPDATE tax_table_row
SET percentage = TRUE
WHERE percentage = FALSE
  AND income_from > 80000;
