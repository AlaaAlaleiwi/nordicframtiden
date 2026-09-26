-- Temporary attachment storage: bytes live on the server only until every
-- room member has fetched them (or until the retention window passes).
-- Deliveries are recorded per member; purged attachments keep their metadata
-- row (name/size) but their bytes are removed.
CREATE TABLE chat_attachment_delivery (
  attachment_id BIGINT NOT NULL REFERENCES chat_attachment(id) ON DELETE CASCADE,
  user_id BIGINT NOT NULL,
  delivered_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (attachment_id, user_id)
);

ALTER TABLE chat_attachment ADD COLUMN purged_at TIMESTAMPTZ;
