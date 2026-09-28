-- GDPR Art. 15/20 email delivery queue: when a user requests their data the
-- request is queued here (after the in-app confirmation) and processed by the
-- nightly 03:00 job, which emails the JSON export to the address on file.
-- The rows double as the audit trail of when the data was delivered.
CREATE TABLE gdpr_export_request (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    username    VARCHAR(120),
    email       VARCHAR(255) NOT NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    attempts    INT          NOT NULL DEFAULT 0,
    last_error  TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    sent_at     TIMESTAMPTZ
);

CREATE INDEX idx_gdpr_export_request_status ON gdpr_export_request (status, created_at);
CREATE INDEX idx_gdpr_export_request_user ON gdpr_export_request (user_id, created_at);
