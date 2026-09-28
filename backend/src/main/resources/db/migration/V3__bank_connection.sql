-- Conexões com bancos via Open Finance (agregador Pluggy) — ver
-- docs/OPEN-FINANCE-E-BOLETOS.md, seção 2.3, para o desenho completo.
--
-- Não guarda nenhuma credencial do banco: isso fica inteiramente do lado da
-- Pluggy. item_id é o identificador que a Pluggy usa para essa conexão
-- específica (devolvido pelo widget "Pluggy Connect" no callback onSuccess).

create table bank_connection (
    id            uuid primary key default uuid_generate_v4(),
    user_id       uuid not null references app_user(id) on delete cascade,
    item_id       varchar(100) not null,
    bank_name     varchar(255) not null,
    status        varchar(20) not null,
    connected_at  timestamptz not null default now(),
    last_sync_at  timestamptz
);
create unique index idx_bank_connection_user_item on bank_connection(user_id, item_id);
create index idx_bank_connection_user on bank_connection(user_id);
