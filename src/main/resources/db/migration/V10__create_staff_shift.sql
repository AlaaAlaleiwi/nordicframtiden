create table if not exists staff_shift (
  id bigserial primary key,
  user_id bigint not null references app_user(id),
  start_at timestamptz not null,
  end_at timestamptz not null,
  note text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index if not exists idx_staff_shift_user_id on staff_shift(user_id);
create index if not exists idx_staff_shift_start_end on staff_shift(start_at, end_at);