create table app_user (
  id bigserial primary key,
  username varchar(150) not null unique,
  password_hash varchar(255) not null,
  enabled boolean not null default true
);

create table app_user_role (
  user_id bigint not null references app_user(id) on delete cascade,
  role varchar(50) not null,
  primary key (user_id, role)
);