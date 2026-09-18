package br.com.financecash.application.dto;

import br.com.financecash.domain.model.EntryType;

import java.math.BigDecimal;
import java.util.UUID;

public record MonthClosingCategoryItem(
        UUID categoryId,
        String categoryName,
        String colorHex,
        EntryType type,
        BigDecimal previsto,
        BigDecimal realizado
) {
}
