package br.com.financecash.application.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CategorySpendingReportItem(
        UUID categoryId,
        String categoryName,
        String colorHex,
        BigDecimal total,
        long entryCount,
        BigDecimal percentageOfTotal
) {
}
