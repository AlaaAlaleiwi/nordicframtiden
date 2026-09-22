CREATE TABLE chat_room (
    id BIGSERIAL PRIMARY KEY,
    type VARCHAR(16) NOT NULL CHECK (type IN ('CHANNEL', 'DIRECT')),
    name VARCHAR(80),
    description VARCHAR(500),
    private_channel BOOLEAN NOT NULL DEFAULT FALSE,
    created_by BIGINT NOT NULL REFERENCES app_user(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chat_channel_name_required CHECK (type <> 'CHANNEL' OR (name IS NOT NULL AND btrim(name) <> ''))
);

CREATE UNIQUE INDEX uq_chat_channel_name_ci
    ON chat_room (lower(name)) WHERE type = 'CHANNEL';

CREATE TABLE chat_room_member (
    room_id BIGINT NOT NULL REFERENCES chat_room(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_read_message_id BIGINT,
    PRIMARY KEY (room_id, user_id)
);

CREATE TABLE chat_message (
    id BIGSERIAL PRIMARY KEY,
    room_id BIGINT NOT NULL REFERENCES chat_room(id) ON DELETE CASCADE,
    sender_id BIGINT NOT NULL REFERENCES app_user(id),
    parent_message_id BIGINT REFERENCES chat_message(id) ON DELETE CASCADE,
    body VARCHAR(4000) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    edited_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ,
    CONSTRAINT chat_message_body_not_blank CHECK (btrim(body) <> '')
);

ALTER TABLE chat_room_member
    ADD CONSTRAINT fk_chat_member_last_read
    FOREIGN KEY (last_read_message_id) REFERENCES chat_message(id) ON DELETE SET NULL;

CREATE INDEX ix_chat_message_room_id ON chat_message(room_id, id DESC);
CREATE INDEX ix_chat_message_parent_id ON chat_message(parent_message_id, id);

CREATE TABLE chat_reaction (
    message_id BIGINT NOT NULL REFERENCES chat_message(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    emoji VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (message_id, user_id, emoji)
);

CREATE INDEX ix_chat_member_user ON chat_room_member(user_id, room_id);
