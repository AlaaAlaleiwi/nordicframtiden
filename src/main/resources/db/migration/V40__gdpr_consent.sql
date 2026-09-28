-- GDPR accountability (Art. 7(1)): record every consent the account holder
-- gives or withdraws, plus the audit trail of access (Art. 15) and erasure
-- (Art. 17) requests. Keeps working when the erased account itself is gone:
-- user_id is a plain column, not a FK, so the erasure of the account never
-- cascades into the consent evidence.
CREATE TABLE gdpr_consent (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT,
    username     VARCHAR(120),
    consent_type VARCHAR(40)  NOT NULL,
    granted      BOOLEAN      NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_gdpr_consent_user ON gdpr_consent (user_id, consent_type, created_at);
