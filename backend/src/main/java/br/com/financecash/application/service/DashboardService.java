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
        return getDashboardForUser(currentUserProvider.getCurrentUser(), referenceDate);
    }

    /**
     * Mesma lógica de {@link #getDashboard(LocalDate)}, mas recebendo o AppUser
     * diretamente em vez de resolvê-lo via CurrentUserProvider/SecurityContext — usado
     * pelo DueSoonAndBalanceNotificationScheduler, que roda fora de uma requisição HTTP
     * (mesma razão pela qual RecurringEntryScheduler não usa CurrentUserProvider).
     */
    @Transactional(readOnly = true)
    public DashboardResponse getDashboardForUser(AppUser user, LocalDate referenceDate) {
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

        // Achado real (29/09, relatado pelo Marcio comparando com a planilha dele — o mesmo
        // R$41.792,99/R$33.347,57 já investigado antes na "quarta rodada do totalizador", ver
        // docs/ROADMAP.md): o Dashboard somava TODOS os lançamentos do mês na conta, inclusive
        // os marcados como `excludedFromTotals` (transferência entre as próprias contas,
        // liquidação de fatura contada em duplicidade no lado da conta que pagou, e qualquer
        // outro que o próprio Marcio excluiu manualmente pelo botão "Excluir do total"). O
        // totalizador da tela de Lançamentos já respeita essa flag desde a quarta rodada
        // (`EntryRepository.sumByFilters*`) — o Dashboard nunca foi atualizado junto, porque
        // calcula os totais à mão em Java a partir de `monthEntries`, sem passar pela mesma
        // query. Corrigido filtrando `excludedFromTotals` antes de somar, sem esconder nada da
        // lista de "Contas de hoje"/"Atrasadas" (essas continuam mostrando todo lançamento,
        // igual a tela de Lançamentos faz com a tag "fora do total").
        List<Entry> summableEntries = monthEntries.stream()
                .filter(e -> !e.isExcludedFromTotals())
                .toList();

        BigDecimal totalPendingToday = sum(summableEntries, e ->
                e.getDueDate().equals(referenceDate) && e.getType() == EntryType.DESPESA && e.getStatus() != EntryStatus.PAGO && e.getStatus() != EntryStatus.CANCELADO);

        // "A pagar no mês": despesas do mês que AINDA não foram pagas (PENDENTE/ATRASADO).
        BigDecimal totalPendingMonth = sum(summableEntries, e ->
                e.getType() == EntryType.DESPESA && e.getStatus() != EntryStatus.PAGO && e.getStatus() != EntryStatus.CANCELADO);

        // "Já pago no mês": despesas do mês com status PAGO.
        BigDecimal totalPaidMonth = sum(summableEntries, e ->
                e.getType() == EntryType.DESPESA && e.getStatus() == EntryStatus.PAGO);

        // Total geral de despesas do mês, independente de já ter sido paga ou não — é o valor
        // "total de contas a pagar no mês" que o Marcio pediu (30/09), separado do que ainda
        // falta pagar (totalPendingMonth).
        BigDecimal totalExpensesMonth = totalPendingMonth.add(totalPaidMonth);

        // Receita já recebida no mês (status PAGO) — esse dinheiro já está refletido no saldo
        // consolidado (que vem do BalanceSnapshot mais recente de cada conta), então NÃO entra
        // na projeção de saldo abaixo.
        BigDecimal totalIncomePaidMonth = sum(summableEntries, e ->
                e.getType() == EntryType.RECEITA && e.getStatus() == EntryStatus.PAGO);

        // Receita prevista do mês que AINDA não foi recebida — é essa, e só essa, que soma na
        // projeção de saldo de fim de mês.
        BigDecimal totalIncomePendingMonth = sum(summableEntries, e ->
                e.getType() == EntryType.RECEITA && e.getStatus() != EntryStatus.PAGO && e.getStatus() != EntryStatus.CANCELADO);

        BigDecimal consolidatedBalance = accountService.consolidatedBalance(user.getId());

        // Saldo projetado pro fim do mês: saldo atual (já reflete tudo que já foi pago/recebido,
        // vem do BalanceSnapshot real de cada conta) + receitas AINDA NÃO recebidas do mês -
        // despesas AINDA NÃO pagas do mês.
        //
        // Achado em produção (30/09): antes desse fix, a fórmula somava TODA receita do mês
        // (incluindo a já recebida/PAGO) em vez de só a pendente — isso contava a mesma receita
        // duas vezes (uma no saldo consolidado, outra na "receita prevista"), inflando o saldo
        // projetado. Corrigido trocando incomePendingMonth por totalIncomePendingMonth, que
        // exclui PAGO.
        BigDecimal projectedBalanceEndOfMonth = consolidatedBalance
                .add(totalIncomePendingMonth)
                .subtract(totalPendingMonth);

        return new DashboardResponse(
                referenceDate, dueToday, overdue,
                totalPendingToday, totalPendingMonth, totalPaidMonth, totalExpensesMonth,
                totalIncomePaidMonth, totalIncomePendingMonth,
                consolidatedBalance, projectedBalanceEndOfMonth);
    }

    private BigDecimal sum(List<Entry> entries, java.util.function.Predicate<Entry> filter) {
        return entries.stream()
                .filter(filter)
                .map(Entry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
