DO $$
BEGIN
  IF to_regclass('public.schedule_shift') IS NULL THEN
    -- Fresh DB: table doesn't exist yet, so nothing to "convert".
    -- Create it in this migration (or leave it to V6 if you have that).
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

  ELSE
    -- Existing DB: table exists, so do the conversion safely
    ALTER TABLE schedule_shift
      ADD COLUMN IF NOT EXISTS start_at timestamptz,
      ADD COLUMN IF NOT EXISTS end_at   timestamptz;

    -- If you had old columns, backfill here (change names if needed!)
    -- UPDATE schedule_shift
    -- SET start_at = COALESCE(start_at, (date::timestamptz + start_time)),
    --     end_at   = COALESCE(end_at,   (date::timestamptz + end_time))
    -- WHERE start_at IS NULL OR end_at IS NULL;

    -- Fallback for any NULLs (prevents NOT NULL failure)
    UPDATE schedule_shift
    SET
      start_at = COALESCE(start_at, now()),
      end_at   = COALESCE(end_at, now() + interval '1 hour')
    WHERE start_at IS NULL OR end_at IS NULL;

    ALTER TABLE schedule_shift
      ALTER COLUMN start_at SET NOT NULL,
      ALTER COLUMN end_at   SET NOT NULL;

    CREATE INDEX IF NOT EXISTS idx_schedule_shift_range
      ON schedule_shift (start_at, end_at);
  END IF;
END $$;