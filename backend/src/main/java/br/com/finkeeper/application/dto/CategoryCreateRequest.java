package br.com.finkeeper.application.dto;

import br.com.finkeeper.domain.model.CategoryType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CategoryCreateRequest(
        @NotBlank String name,
        @NotNull CategoryType type,
        String colorHex
) {
}
