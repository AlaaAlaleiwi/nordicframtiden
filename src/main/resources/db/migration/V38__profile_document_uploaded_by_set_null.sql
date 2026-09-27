-- Fix DELETE /api/admins/{id} returning 500: an admin who uploaded profile
-- documents to other users' profiles blocks the delete because
-- profile_document.uploaded_by had no ON DELETE action. Keep the document,
-- keep the audit info in the new snapshot column, drop the FK link.
ALTER TABLE profile_document
  DROP CONSTRAINT IF EXISTS profile_document_uploaded_by_fkey;

ALTER TABLE profile_document
  ADD CONSTRAINT profile_document_uploaded_by_fkey
  FOREIGN KEY (uploaded_by) REFERENCES app_user(id) ON DELETE SET NULL;

-- Preserve who uploaded each document as plain data (survives uploader deletion).
ALTER TABLE profile_document
  ADD COLUMN uploaded_by_name VARCHAR(160);
