CREATE TABLE call_history (
    call_id UUID PRIMARY KEY,
    room_id BIGINT NOT NULL REFERENCES chat_room(id) ON DELETE CASCADE,
    caller_id BIGINT NOT NULL REFERENCES app_user(id),
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    answered_at TIMESTAMPTZ,
    ended_at TIMESTAMPTZ,
    outcome VARCHAR(20) NOT NULL DEFAULT 'RINGING'
);

CREATE INDEX ix_call_history_room_started ON call_history(room_id, started_at DESC);
