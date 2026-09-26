-- Attachment-only chat messages have an empty body. The CHECK below was
-- created before attachments existed (V21) and rejects them, so any message
-- sent with an attachment but no caption failed with a constraint violation.
-- Row-level validation now lives in ChatService (a message must contain
-- text, attachments, or both); the DB-level check is dropped.
ALTER TABLE chat_message DROP CONSTRAINT IF EXISTS chat_message_body_not_blank;
