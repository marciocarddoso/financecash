package br.com.financecash.application.dto;

import br.com.financecash.domain.model.AccountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Mesmos campos do AccountCreateRequest — permite corrigir nome/banco/tipo depois de criada. */
public record AccountUpdateRequest(
        @NotBlank String name,
        @NotBlank String bankName,
        @NotNull AccountType type,
        String investmentDescription
) {
}
