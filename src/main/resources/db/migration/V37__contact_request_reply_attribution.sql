-- Who replied to a contact request (display name + username) and when is
-- already covered by handled_at; these columns add the actor attribution.
ALTER TABLE contact_request
  ADD COLUMN handled_by_name VARCHAR(160),
  ADD COLUMN handled_by_username VARCHAR(60);
