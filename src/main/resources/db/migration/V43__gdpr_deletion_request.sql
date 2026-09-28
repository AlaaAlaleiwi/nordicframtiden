-- GDPR Art. 17 deletion requests (admin-reviewed, scheduled execution):
-- deleting a user cascades into their schedule shifts and therefore the
-- payroll/payment records derived from them, so erasure is never immediate.
-- The data subject files a request; an ADMIN approves with a scheduled date
-- (at least 30 days out); the nightly job deletes after that date; the user
-- is emailed at request, approval and completion. Rows survive the deletion
-- as the accountability trail (user_id is intentionally not a FK).
CREATE TABLE gdpr_deletion_request (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT       NOT NULL,
    username         VARCHAR(120),
    email            VARCHAR(255),
    status           VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    reason           VARCHAR(1000),
    scheduled_date   DATE,
    approved_by      VARCHAR(120),
    approved_at      TIMESTAMPTZ,
    rejected_by      VARCHAR(120),
    rejected_reason  VARCHAR(1000),
    deleted_at       TIMESTAMPTZ,
    cancelled_at     TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT gdpr_deletion_request_status CHECK (
        status IN ('PENDING', 'APPROVED', 'REJECTED', 'DELETED', 'CANCELLED'))
);

CREATE INDEX idx_gdpr_deletion_request_status ON gdpr_deletion_request (status, scheduled_date);
CREATE INDEX idx_gdpr_deletion_request_user ON gdpr_deletion_request (user_id, created_at);
