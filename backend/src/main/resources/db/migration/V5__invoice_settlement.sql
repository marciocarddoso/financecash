-- Registro por transação da Pluggy de liquidação/financiamento de fatura de cartão (ex.:
-- "Inclusao de Pagamento Ciclo Corrente", "PAGTO. POR DEB EM C/C"), que antes era só ignorado
-- na importação. Usado pra conferência automática entre o total calculado a partir das compras
-- importadas e o que o banco realmente informou pra aquela fatura — ver
-- TransactionImportService e InvoiceSettlement.

create table invoice_settlement (
    id                    uuid primary key default uuid_generate_v4(),
    credit_card_id        uuid not null references credit_card(id) on delete cascade,
    cycle_due_date        date not null,
    amount                numeric(15,2) not null,
    transaction_date      date not null,
    description           varchar(255) not null,
    pluggy_transaction_id varchar(255) not null,
    created_at            timestamptz not null,
    constraint uq_invoice_settlement_card_tx unique (credit_card_id, pluggy_transaction_id)
);
create index idx_invoice_settlement_card_cycle on invoice_settlement(credit_card_id, cycle_due_date);
