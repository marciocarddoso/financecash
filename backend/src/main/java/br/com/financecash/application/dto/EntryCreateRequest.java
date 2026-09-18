package br.com.financecash.application.dto;

import br.com.financecash.domain.model.EntryType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record EntryCreateRequest(
        @NotBlank String description,
        @NotNull @Positive BigDecimal amount,
        @NotNull LocalDate dueDate,
        @NotNull EntryType type,
        @NotNull UUID categoryId,
        UUID accountId,
        UUID creditCardId
) {
}
