package br.com.finkeeper.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CategorySpendingReportResponse(
        LocalDate from,
        LocalDate to,
        BigDecimal totalSpent,
        List<CategorySpendingReportItem> items
) {
}
