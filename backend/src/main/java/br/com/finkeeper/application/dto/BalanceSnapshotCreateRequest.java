package br.com.finkeeper.application.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BalanceSnapshotCreateRequest(
        @NotNull LocalDate referenceDate,
        @NotNull BigDecimal balance
) {
}
