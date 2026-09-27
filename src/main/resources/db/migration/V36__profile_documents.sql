-- Encrypted documents attached to a user profile (IDs, certificates, contracts...).
-- Bytes are AES-256-GCM encrypted at rest with a per-document random IV; the key
-- comes from APP_DOCUMENT_KEY. Only metadata is queryable; the payload is opaque.

CREATE TABLE profile_document (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    file_name     VARCHAR(255) NOT NULL,
    content_type  VARCHAR(255) NOT NULL,
    size_bytes    BIGINT NOT NULL,          -- plaintext size for display
    iv            BYTEA NOT NULL,           -- 12-byte AES-GCM IV per document
    data          BYTEA NOT NULL,           -- ciphertext (never leaves the server unencrypted)
    uploaded_by   BIGINT REFERENCES app_user(id),
    created_at    TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_profile_document_user ON profile_document(user_id);
