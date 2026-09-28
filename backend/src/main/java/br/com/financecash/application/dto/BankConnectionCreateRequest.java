package br.com.financecash.application.dto;

import jakarta.validation.constraints.NotBlank;

/** itemId devolvido pelo widget "Pluggy Connect" no callback onSuccess ({ item: { id } }). */
public record BankConnectionCreateRequest(@NotBlank String itemId) {
}
