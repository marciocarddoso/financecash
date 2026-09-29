-- Marca lançamentos que não devem entrar na soma de despesa/receita do totalizador (ver
-- EntryRepository.sumByFilters), mas continuam aparecendo normalmente na lista de Lançamentos —
-- a pedido do Marcio (01/10): "o total de despesa e o de receita estão fora da realidade".
-- Dois casos identificados com dado real, comparando a soma de setembro por categoria:
--   (a) transferência entre as próprias contas do usuário (ex. Bradesco -> Nubank) — dinheiro
--       mudando de bolso, não é gasto nem receita; conta nos dois lados e infla os dois totais
--       sem nunca aparecer no saldo líquido (uma perna cancela a outra);
--   (b) o débito em conta corrente que paga a fatura do cartão — o gasto real já foi contado
--       quando a compra foi feita no cartão (IMPORTADO_CARTAO); contar esse débito de novo
--       (IMPORTADO_EXTRATO) duplica o mesmo dinheiro.
-- Ver TransactionImportService.importForAccount / isExcludedFromTotals.

alter table entry add column excluded_from_totals boolean not null default false;
