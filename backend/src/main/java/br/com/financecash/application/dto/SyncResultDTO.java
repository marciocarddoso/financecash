package br.com.financecash.application.dto;

/**
 * Resumo de uma sincronização de contas Pluggy → FinanceCash (AccountSyncService).
 * creditCardsSkipped conta as contas do tipo CREDIT trazidas pela Pluggy que ainda não
 * são sincronizadas (cartões de crédito ficam pra uma próxima fase — ver docs/ROADMAP.md).
 */
public record SyncResultDTO(int accountsCreated, int accountsUpdated, int creditCardsSkipped) {
}
