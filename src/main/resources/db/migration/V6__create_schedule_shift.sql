CREATE TABLE IF NOT EXISTS schedule_shift (
  id bigserial PRIMARY KEY,
  pharmacy_id bigint NOT NULL REFERENCES pharmacy(id) ON DELETE CASCADE,
  user_id bigint NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,

  start_at timestamptz NOT NULL,
  end_at timestamptz NOT NULL,

  note varchar(300),

  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_schedule_shift_range
  ON schedule_shift (start_at, end_at);

CREATE INDEX IF NOT EXISTS idx_schedule_shift_pharmacy
  ON schedule_shift (pharmacy_id);

CREATE INDEX IF NOT EXISTS idx_schedule_shift_user
  ON schedule_shift (user_id);