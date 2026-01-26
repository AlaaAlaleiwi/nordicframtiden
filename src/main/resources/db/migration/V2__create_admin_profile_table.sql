create table admin_profile (
  id bigserial primary key,
  full_name varchar(255) not null,
  email varchar(255) not null,
  phone varchar(50) not null,
  user_id bigint not null unique,
  constraint fk_admin_profile_user foreign key (user_id) references app_user(id),
  constraint uk_admin_email unique (email),
  constraint uk_admin_phone unique (phone)
);