-- Frozen payslips: once a salary month has ended it is calculated once and
-- persisted. Later changes to the hourly cost (or other profile data) must
-- never retroactively change history; past months are served from this table.
CREATE TABLE payslip_snapshot (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  year INT NOT NULL,
  month INT NOT NULL,
  role VARCHAR(16) NOT NULL DEFAULT 'USER',
  payload TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_payslip_snapshot UNIQUE (user_id, year, month, role)
);

CREATE INDEX idx_payslip_snapshot_user ON payslip_snapshot (user_id, year, month);
