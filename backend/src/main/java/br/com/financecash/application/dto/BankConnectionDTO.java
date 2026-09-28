package br.com.financecash.application.dto;

import br.com.financecash.domain.model.BankConnection;
import br.com.financecash.domain.model.BankConnectionStatus;

import java.time.Instant;
import java.util.UUID;

public record BankConnectionDTO(
        UUID id,
        String bankName,
        BankConnectionStatus status,
        Instant connectedAt,
        Instant lastSyncAt
) {
    public static BankConnectionDTO from(BankConnection connection) {
        return new BankConnectionDTO(
                connection.getId(),
                connection.getBankName(),
                connection.getStatus(),
                connection.getConnectedAt(),
                connection.getLastSyncAt());
    }
}
