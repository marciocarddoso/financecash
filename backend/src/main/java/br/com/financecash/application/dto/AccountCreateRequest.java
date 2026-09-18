package br.com.financecash.application.dto;

import br.com.financecash.domain.model.AccountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AccountCreateRequest(
        @NotBlank String name,
        @NotBlank String bankName,
        @NotNull AccountType type,
        String investmentDescription
) {
}
