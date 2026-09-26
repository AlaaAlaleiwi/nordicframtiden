-- Single-use, time-limited tokens for the password reset flow
-- (self-service from the login page and admin-initiated resets).
-- Only the SHA-256 hash of the token is stored; the raw token lives only
-- in the email link.
CREATE TABLE password_reset_token (
  id BIGSERIAL PRIMARY KEY,
  token_hash VARCHAR(64) NOT NULL UNIQUE,
  user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  expires_at TIMESTAMPTZ NOT NULL,
  used_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_password_reset_token_user ON password_reset_token (user_id);
