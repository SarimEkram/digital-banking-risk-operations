-- V3: Payees (internal-only). Users can add payees by email, disable them, and re-add to re-enable.

create table if not exists payees (
  id bigserial primary key,

  owner_user_id bigint not null references users(id) on delete restrict,
  payee_user_id bigint not null references users(id) on delete restrict,

  payee_email text not null,
  label text null,

  status text not null default 'ACTIVE'
    check (status in ('ACTIVE','DISABLED')),

  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),

  -- prevent adding yourself
  constraint chk_payees_not_self check (owner_user_id <> payee_user_id)
);

-- fast list
create index if not exists idx_payees_owner_user_id on payees(owner_user_id);
create index if not exists idx_payees_owner_status on payees(owner_user_id, status);

-- prevent duplicates (case-insensitive by email, and also by user id)
create unique index if not exists uq_payees_owner_payee_user_id
  on payees(owner_user_id, payee_user_id);

create unique index if not exists uq_payees_owner_payee_email_ci
  on payees(owner_user_id, lower(payee_email));
