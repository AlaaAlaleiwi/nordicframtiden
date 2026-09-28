-- Preserve the last surviving legacy snapshot exactly; earlier overwritten versions
-- cannot be reconstructed. New finalizations and corrections are append-only.
CREATE TABLE payslip_revision (
  id BIGSERIAL PRIMARY KEY,
  snapshot_id BIGINT NOT NULL REFERENCES payslip_snapshot(id) ON DELETE CASCADE,
  revision INT NOT NULL CHECK (revision > 0),
  actor VARCHAR(255) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  reason TEXT NOT NULL,
  changes TEXT,
  payload TEXT NOT NULL,
  UNIQUE (snapshot_id, revision)
);
INSERT INTO payslip_revision (snapshot_id, revision, actor, created_at, reason, payload)
SELECT id, 1, 'legacy-import', updated_at,
       'Preserved legacy snapshot; original operator and earlier versions unavailable', payload
FROM payslip_snapshot;

CREATE FUNCTION prevent_payslip_rewrite() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  RAISE EXCEPTION 'Finalized payslips are immutable; append a correction revision';
END;
$$;
CREATE TRIGGER immutable_payslip_snapshot BEFORE UPDATE ON payslip_snapshot
FOR EACH ROW EXECUTE FUNCTION prevent_payslip_rewrite();
CREATE TRIGGER immutable_payslip_revision BEFORE UPDATE ON payslip_revision
FOR EACH ROW EXECUTE FUNCTION prevent_payslip_rewrite();
-- DELETE remains available for the existing explicit account-erasure workflow.
