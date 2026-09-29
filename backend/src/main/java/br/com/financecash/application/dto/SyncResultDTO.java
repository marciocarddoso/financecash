package br.com.financecash.application.dto;

public record SyncResultDTO(
        int accountsCreated,
        int accountsUpdated,
        int accountsSkipped,
        int creditCardsCreated,
        int creditCardsUpdated,
        int creditCardsSkipped,
        int creditCardsWithEstimatedClosingDay,
        int entriesImported
) {
}
