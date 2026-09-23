ALTER TABLE chat_room_member
    ADD COLUMN channel_admin BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE chat_room_member member
SET channel_admin = TRUE
FROM chat_room room
WHERE member.room_id = room.id
  AND room.type = 'CHANNEL'
  AND member.user_id = room.created_by;
