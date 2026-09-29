package br.com.financecash.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * @param totalPendingToday       despesas com vencimento hoje, ainda não pagas.
 * @param totalPendingMonth       despesas do mês inteiro que ainda faltam pagar (PENDENTE/ATRASADO).
 * @param totalPaidMonth          despesas do mês que já foram pagas.
 * @param totalExpensesMonth      total geral de despesas do mês (totalPendingMonth + totalPaidMonth).
 * @param totalIncomePaidMonth    receita do mês já recebida (status PAGO) — já refletida em consolidatedBalance.
 * @param totalIncomePendingMonth receita do mês prevista, ainda não recebida.
 * @param consolidatedBalance     soma do saldo mais recente (BalanceSnapshot) de cada conta ativa.
 * @param projectedBalanceEndOfMonth consolidatedBalance + totalIncomePendingMonth - totalPendingMonth.
 */
public record DashboardResponse(
        LocalDate referenceDate,
        List<EntryDTO> dueToday,
        List<EntryDTO> overdue,
        BigDecimal totalPendingToday,
        BigDecimal totalPendingMonth,
        BigDecimal totalPaidMonth,
        BigDecimal totalExpensesMonth,
        BigDecimal totalIncomePaidMonth,
        BigDecimal totalIncomePendingMonth,
        BigDecimal consolidatedBalance,
        BigDecimal projectedBalanceEndOfMonth
) {
}
