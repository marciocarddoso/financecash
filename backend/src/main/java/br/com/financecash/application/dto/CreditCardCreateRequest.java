package br.com.financecash.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CreditCardCreateRequest(
        @NotBlank String name,
        @NotBlank String bankName,
        @Min(1) @Max(31) int closingDay,
        @Min(1) @Max(31) int dueDay
) {
}
