package br.com.financecash.application.dto;

/**
 * Resumo de uma sincronização de contas/cartões Pluggy → FinanceCash (AccountSyncService).
 * creditCardsSkipped conta cartões que a Pluggy trouxe mas que não deu pra sincronizar com
 * segurança (ex.: sem creditData.balanceDueDate, ou conexão sem nenhuma conta BANK pra
 * identificar o banco). creditCardsWithEstimatedClosingDay conta, dentre os criados, quantos
 * tiveram o dia de fechamento ESTIMADO (a Pluggy não manda balanceCloseDate nos conectores
 * testados) — vale a pena conferir/corrigir esses via "Editar" na tela Cartões.
 */
public record SyncResultDTO(
        int accountsCreated,
        int accountsUpdated,
        int creditCardsCreated,
        int creditCardsUpdated,
        int creditCardsSkipped,
        int creditCardsWithEstimatedClosingDay
) {
}
