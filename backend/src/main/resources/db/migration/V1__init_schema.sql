-- Schema inicial do FinKeeper. Ver docs/MODELO-DOMINIO.md para a explicação de cada entidade.

create extension if not exists "uuid-ossp";

create table app_user (
    id              uuid primary key default uuid_generate_v4(),
    email           varchar(255) not null unique,
    name            varchar(255) not null,
    password_hash   varchar(255) not null,
    active          boolean not null default true,
    created_at      timestamptz not null default now()
);

create table account (
    id                      uuid primary key default uuid_generate_v4(),
    user_id                 uuid not null references app_user(id) on delete cascade,
    name                    varchar(255) not null,
    bank_name               varchar(255) not null,
    type                    varchar(20) not null,
    investment_description  varchar(255),
    active                  boolean not null default true
);
create index idx_account_user on account(user_id);

create table balance_snapshot (
    id              uuid primary key default uuid_generate_v4(),
    account_id      uuid not null references account(id) on delete cascade,
    reference_date  date not null,
    balance         numeric(15,2) not null,
    created_at      timestamptz not null default now()
);
create index idx_balance_snapshot_account on balance_snapshot(account_id, reference_date desc);

create table category (
    id          uuid primary key default uuid_generate_v4(),
    user_id     uuid not null references app_user(id) on delete cascade,
    name        varchar(255) not null,
    type        varchar(20) not null,
    color_hex   varchar(7),
    active      boolean not null default true
);
create index idx_category_user on category(user_id);

create table credit_card (
    id          uuid primary key default uuid_generate_v4(),
    user_id     uuid not null references app_user(id) on delete cascade,
    name        varchar(255) not null,
    bank_name   varchar(255) not null,
    closing_day int not null,
    due_day     int not null,
    active      boolean not null default true
);
create index idx_credit_card_user on credit_card(user_id);

create table recurring_rule (
    id          uuid primary key default uuid_generate_v4(),
    user_id     uuid not null references app_user(id) on delete cascade,
    name        varchar(255) not null,
    type        varchar(20) not null,
    category_id uuid not null references category(id),
    account_id  uuid references account(id),
    frequency   varchar(20) not null,
    day_of_month int not null,
    start_date  date not null,
    end_date    date,
    active      boolean not null default true
);
create index idx_recurring_rule_user on recurring_rule(user_id);

create table recurring_rule_reference_month (
    recurring_rule_id  uuid not null references recurring_rule(id) on delete cascade,
    month               int not null
);

create table recurring_rule_value_history (
    id                  uuid primary key default uuid_generate_v4(),
    recurring_rule_id   uuid not null references recurring_rule(id) on delete cascade,
    effective_from       date not null,
    amount               numeric(15,2) not null
);
create index idx_recurring_value_rule on recurring_rule_value_history(recurring_rule_id, effective_from desc);

create table installment_plan (
    id                  uuid primary key default uuid_generate_v4(),
    user_id             uuid not null references app_user(id) on delete cascade,
    description         varchar(255) not null,
    total_amount        numeric(15,2) not null,
    installments_count  int not null,
    first_due_date      date not null,
    category_id         uuid not null references category(id),
    account_id          uuid references account(id),
    credit_card_id       uuid references credit_card(id)
);
create index idx_installment_plan_user on installment_plan(user_id);

create table entry (
    id                  uuid primary key default uuid_generate_v4(),
    user_id             uuid not null references app_user(id) on delete cascade,
    description         varchar(255) not null,
    amount              numeric(15,2) not null,
    due_date            date not null,
    payment_date        date,
    type                varchar(20) not null,
    status              varchar(20) not null default 'PENDENTE',
    origin              varchar(20) not null default 'MANUAL',
    category_id         uuid not null references category(id),
    account_id          uuid references account(id),
    recurring_rule_id    uuid references recurring_rule(id),
    installment_plan_id  uuid references installment_plan(id),
    installment_number  int,
    credit_card_id       uuid references credit_card(id)
);
create index idx_entry_user_due_date on entry(user_id, due_date);
create index idx_entry_recurring_rule on entry(recurring_rule_id);
create index idx_entry_installment_plan on entry(installment_plan_id);
create index idx_entry_category on entry(category_id);

create table transfer (
    id                  uuid primary key default uuid_generate_v4(),
    entry_id            uuid not null unique references entry(id) on delete cascade,
    recipient_name      varchar(255) not null,
    pix_key_masked       varchar(64),
    recurring            boolean not null default false
);
