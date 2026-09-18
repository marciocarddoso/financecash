package br.com.financecash.application.dto;

import jakarta.validation.constraints.NotBlank;

public record BoletoParseRequest(@NotBlank String linhaDigitavel) {
}
