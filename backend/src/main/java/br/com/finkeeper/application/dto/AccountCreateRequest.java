package br.com.finkeeper.application.dto;

import br.com.finkeeper.domain.model.AccountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AccountCreateRequest(
        @NotBlank String name,
        @NotBlank String bankName,
        @NotNull AccountType type,
        String investmentDescription
) {
}
