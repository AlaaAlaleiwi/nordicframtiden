-- Availability requests gain an optional daily working-hours window
-- (from–to). Pharmacists state when they can work; admins see the window
-- when reviewing the request and when picking staff for a shift.
ALTER TABLE availability_request
  ADD COLUMN start_time TIME,
  ADD COLUMN end_time TIME;
