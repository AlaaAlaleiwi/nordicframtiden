ALTER TABLE profile_document
    ADD COLUMN shared_with_user BOOLEAN NOT NULL DEFAULT FALSE;

-- Documents uploaded by their owner belong to the employee and remain visible
-- to them. Existing admin uploads stay private until an admin shares them.
UPDATE profile_document
SET shared_with_user = TRUE
WHERE uploaded_by = user_id;
