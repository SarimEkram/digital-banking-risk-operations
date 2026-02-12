-- V1: core schema foundation (users, accounts, transfers, ledger, idempotency, audit)

create table if not exists users (
  id bigserial primary key,
  email text not null unique,
  password_hash text not null,
  role text not null default 'USER' check (role in ('USER','ADMIN')),
  created_at timestamptz not null default now()
);

create table if not exists accounts (
  id bigserial primary key,
  user_id bigint not null references users(id) on delete restrict,

  -- NEW: account type (savings comes later, but we support the type now)
  account_type text not null default 'CHEQUING'
    check (account_type in ('CHEQUING','SAVINGS')),

  currency varchar(3) not null default 'CAD',
  balance_cents bigint not null default 0,
  status text not null default 'ACTIVE' check (status in ('ACTIVE','FROZEN','CLOSED')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index if not exists idx_accounts_user_id on accounts(user_id);

-- NEW: enforce max 1 account per (user, type)
create unique index if not exists uq_accounts_user_id_account_type
  on accounts(user_id, account_type);

create table if not exists transfers (
  id bigserial primary key,
  from_account_id bigint not null references accounts(id) on delete restrict,
  to_account_id bigint not null references accounts(id) on delete restrict,
  amount_cents bigint not null check (amount_cents > 0),
  currency varchar(3) not null default 'CAD',
  status text not null check (status in ('INITIATED','PENDING_REVIEW','BLOCKED','REJECTED','COMPLETED')),
  risk_decision text null check (risk_decision in ('APPROVE','HOLD','BLOCK')),
  risk_score int null check (risk_score between 0 and 100),
  risk_reasons text null,
  idempotency_key text not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index if not exists idx_transfers_from_account on transfers(from_account_id);
create index if not exists idx_transfers_to_account on transfers(to_account_id);
create index if not exists idx_transfers_status on transfers(status);
create unique index if not exists uq_transfers_idempotency_key on transfers(idempotency_key);

create table if not exists ledger_entries (
  id bigserial primary key,
  transfer_id bigint references transfers(id) on delete set null,
  account_id bigint not null references accounts(id) on delete restrict,
  direction text not null check (direction in ('DEBIT','CREDIT')),
  amount_cents bigint not null check (amount_cents > 0),
  currency varchar(3) not null default 'CAD',
  created_at timestamptz not null default now()
);

create index if not exists idx_ledger_entries_account_id on ledger_entries(account_id);
create index if not exists idx_ledger_entries_transfer_id on ledger_entries(transfer_id);

create table if not exists idempotency_keys (
  id bigserial primary key,
  key text not null unique,
  request_hash text not null,
  response_code int null,
  response_body text null,
  created_at timestamptz not null default now(),
  expires_at timestamptz not null
);

create table if not exists audit_log (
  id bigserial primary key,
  actor_user_id bigint references users(id) on delete set null,
  action text not null,
  entity_type text not null,
  entity_id text not null,
  details text null,
  correlation_id text null,
  created_at timestamptz not null default now()
);

create index if not exists idx_audit_actor_created_at
  on audit_log(actor_user_id, created_at);
