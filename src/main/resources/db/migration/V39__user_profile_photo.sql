-- Profile photo: one encrypted profile_document row linked from app_user so
-- admins (no user_profile row) can have a photo too. photo_updated_at is a
-- cache-busting version clients use when downloading the photo bytes.
ALTER TABLE app_user
    ADD COLUMN photo_id BIGINT REFERENCES profile_document(id) ON DELETE SET NULL,
    ADD COLUMN photo_updated_at TIMESTAMPTZ;
