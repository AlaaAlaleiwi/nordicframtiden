ALTER TABLE salary_adjustment ADD COLUMN reimbursement_type VARCHAR(40);
ALTER TABLE salary_adjustment ADD COLUMN quantity NUMERIC(10,2);
ALTER TABLE salary_adjustment ADD COLUMN receipt_reference VARCHAR(160);
ALTER TABLE salary_adjustment ADD COLUMN tax_free_eligibility_confirmed BOOLEAN NOT NULL DEFAULT FALSE;
