create table if not exists pharmacy (
  id bigserial primary key,
  name varchar(160) not null,
  email varchar(160),
  phone varchar(40),
  address varchar(220),

  -- 👤 contact person (single contact)
  contact_name varchar(160),
  contact_email varchar(160),
  contact_phone varchar(40),

  enabled boolean not null default true
);

create unique index if not exists uk_pharmacy_name on pharmacy (lower(name));