package br.com.financecash.application.service;

import br.com.financecash.application.dto.DashboardResponse;
import br.com.financecash.application.dto.EntryDTO;
import br.com.financecash.domain.model.AppUser;
import br.com.financecash.domain.model.Entry;
import br.com.financecash.domain.model.EntryStatus;
import br.com.financecash.domain.model.EntryType;
import br.com.financecash.domain.repository.EntryRepository;
import br.com.financecash.security.CurrentUserProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * Responde à pergunta central do pedido original: "quais são as contas do dia a
 * serem pagas e quanto de saldo eu preciso para o dia e para o mês".
 */
@Service
public class DashboardService {

    private final EntryRepository entryRepository;
    private final AccountService accountService;
    private final CurrentUserProvider currentUserProvider;

    public DashboardService(EntryRepository entryRepository, AccountService accountService,
                             CurrentUserProvider currentUserProvider) {
        this.entryRepository = entryRepository;
        this.accountService = accountService;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional(readOnly = true)
    public DashboardResponse getDashboard(LocalDate referenceDate) {
        AppUser user = currentUserProvider.getCurrentUser();
        YearMonth month = YearMonth.from(referenceDate);
        LocalDate monthStart = month.atDay(1);
        LocalDate monthEnd = month.atEndOfMonth();

        List<Entry> monthEntries = entryRepository
                .findByOwnerIdAndDueDateBetweenOrderByDueDateAsc(user.getId(), monthStart, monthEnd);

        List<EntryDTO> dueToday = monthEntries.stream()
                .filter(e -> e.getDueDate().equals(referenceDate) && e.getStatus() != EntryStatus.PAGO && e.getStatus() != EntryStatus.CANCELADO)
                .map(EntryDTO::from)
                .toList();

        List<EntryDTO> overdue = monthEntries.stream()
                .filter(e -> e.isOverdueAsOf(referenceDate))
                .map(EntryDTO::from)
                .toList();

        BigDecimal totalPendingToday = sum(monthEntries, e ->
                e.getDueDate().equals(referenceDate) && e.getType() == EntryType.DESPESA && e.getStatus() != EntryStatus.PAGO && e.getStatus() != EntryStatus.CANCELADO);

        BigDecimal totalPendingMonth = sum(monthEntries, e ->
                e.getType() == EntryType.DESPESA && e.getStatus() != EntryStatus.PAGO && e.getStatus() != EntryStatus.CANCELADO);

        BigDecimal totalPaidMonth = sum(monthEntries, e ->
                e.getType() == EntryType.DESPESA && e.getStatus() == EntryStatus.PAGO);

        BigDecimal incomePendingMonth = sum(monthEntries, e ->
                e.getType() == EntryType.RECEITA && e.getStatus() != EntryStatus.CANCELADO);

        BigDecimal consolidatedBalance = accountService.consolidatedBalance(user.getId());

        // Saldo necessário/projetado: saldo atual + receitas previstas do mês - despesas pendentes do mês.
        BigDecimal projectedBalanceEndOfMonth = consolidatedBalance
                .add(incomePendingMonth)
                .subtract(totalPendingMonth);

        return new DashboardResponse(
                referenceDate, dueToday, overdue,
                totalPendingToday, totalPendingMonth, totalPaidMonth,
                consolidatedBalance, projectedBalanceEndOfMonth);
    }

    private BigDecimal sum(List<Entry> entries, java.util.function.Predicate<Entry> filter) {
        return entries.stream()
                .filter(filter)
                .map(Entry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
