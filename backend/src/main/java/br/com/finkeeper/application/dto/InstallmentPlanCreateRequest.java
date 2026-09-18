package br.com.finkeeper.application.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record InstallmentPlanCreateRequest(
        @NotBlank String description,
        @NotNull @Positive BigDecimal totalAmount,
        @Min(2) @Max(96) int installmentsCount,
        @NotNull LocalDate firstDueDate,
        @NotNull UUID categoryId,
        UUID accountId,
        UUID creditCardId
) {
}
