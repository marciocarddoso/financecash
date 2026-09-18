package br.com.finkeeper.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record DashboardResponse(
        LocalDate referenceDate,
        List<EntryDTO> dueToday,
        List<EntryDTO> overdue,
        BigDecimal totalPendingToday,
        BigDecimal totalPendingMonth,
        BigDecimal totalPaidMonth,
        BigDecimal consolidatedBalance,
        BigDecimal projectedBalanceEndOfMonth
) {
}
