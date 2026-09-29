-- Automatic payslip delivery queue: when a work month's payslip becomes ready
-- (the 21st of the payment month, or the previous working day when the 21st is
-- not a working day) the scheduled job queues one row per USER/STAFF account,
-- then emails the payslip PDF and sends the "payslip ready" notification.
-- Rows double as the audit trail of when each payslip was delivered.
-- Exactly-once: a worker atomically claims PENDING -> SENDING before it mails,
-- so a crash mid-send or an overlapping run can never send a payslip twice.
CREATE TABLE payslip_delivery_request (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    email      VARCHAR(255) NOT NULL,
    work_year  INT          NOT NULL,
    work_month INT          NOT NULL,
    role       VARCHAR(10)  NOT NULL,
    status     VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    attempts   INT          NOT NULL DEFAULT 0,
    last_error TEXT,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    claimed_at TIMESTAMPTZ,
    sent_at    TIMESTAMPTZ,
    CONSTRAINT fk_payslip_delivery_user FOREIGN KEY (user_id)
        REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT uq_payslip_delivery_month UNIQUE (user_id, work_year, work_month, role)
);

CREATE INDEX idx_payslip_delivery_status ON payslip_delivery_request (status, created_at);
CREATE INDEX idx_payslip_delivery_user ON payslip_delivery_request (user_id, work_year DESC);
