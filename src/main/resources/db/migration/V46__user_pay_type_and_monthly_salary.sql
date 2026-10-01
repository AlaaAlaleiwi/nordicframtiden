-- Employees can be paid hourly (existing hourly_cost) or on a monthly
-- salary. MONTHLY employees' payslip gross is the fixed monthly salary;
-- shifts stay informational (hours are still tracked, no shift pay).
ALTER TABLE user_profile
ADD COLUMN pay_type VARCHAR(10) NOT NULL DEFAULT 'HOURLY';

ALTER TABLE user_profile
ADD COLUMN monthly_salary NUMERIC(12,2);
