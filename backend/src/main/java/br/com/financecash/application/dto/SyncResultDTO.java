package br.com.financecash.application.dto;

public record SyncResultDTO(
        int accountsCreated,
        int accountsUpdated,
        int creditCardsCreated,
        int creditCardsUpdated,
        int creditCardsSkipped,
        int creditCardsWithEstimatedClosingDay,
        int entriesImported
) {
}
