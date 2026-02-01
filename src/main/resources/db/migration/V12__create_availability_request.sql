create table availability_request (
  id bigserial primary key,
  user_id bigint not null references app_user(id),
  type varchar(16) not null,
  start_date date not null,
  end_date date not null,
  status varchar(16) not null,
  note varchar(500),
  created_at timestamptz not null,
  updated_at timestamptz not null
);

create index idx_availability_user on availability_request(user_id);
create index idx_availability_created on availability_request(created_at);