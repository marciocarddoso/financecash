package br.com.financecash.application.dto;

import br.com.financecash.domain.model.EntryType;
import br.com.financecash.domain.model.RecurrenceFrequency;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record RecurringRuleCreateRequest(
        @NotBlank String name,
        @NotNull EntryType type,
        @NotNull UUID categoryId,
        UUID accountId,
        @NotNull RecurrenceFrequency frequency,
        @Min(1) @Max(31) int dayOfMonth,
        List<@Min(1) @Max(12) Integer> referenceMonths,
        @NotNull LocalDate startDate,
        LocalDate endDate,
        @NotNull @Positive BigDecimal initialAmount
) {
}
