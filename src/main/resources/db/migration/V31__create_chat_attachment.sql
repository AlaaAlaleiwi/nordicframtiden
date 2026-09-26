-- Chat attachments: files/images sent in chat. Bytes live in Postgres so
-- Cloud Run's ephemeral filesystem is never involved. Attachments are
-- uploaded first (unbound), then bound to a message when it is sent.
CREATE TABLE chat_attachment (
  id BIGSERIAL PRIMARY KEY,
  message_id BIGINT,
  uploader_id BIGINT NOT NULL,
  file_name VARCHAR(255) NOT NULL,
  content_type VARCHAR(127) NOT NULL,
  size_bytes BIGINT NOT NULL,
  data BYTEA NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_chat_attachment_message ON chat_attachment (message_id);
