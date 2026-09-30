package br.com.financecash.application.service;

import br.com.financecash.application.dto.DashboardResponse;
import br.com.financecash.domain.model.AppUser;
import br.com.financecash.domain.model.Category;
import br.com.financecash.domain.model.CategoryType;
import br.com.financecash.domain.model.Entry;
import br.com.financecash.domain.model.EntryOrigin;
import br.com.financecash.domain.model.EntryStatus;
import br.com.financecash.domain.model.EntryType;
import br.com.financecash.domain.repository.EntryRepository;
import br.com.financecash.security.CurrentUserProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Cobre principalmente o achado em produção (30/09): o saldo projetado estava somando receita
 * já recebida (PAGO) de novo, além do saldo consolidado que já refletia esse dinheiro — ver
 * javadoc de DashboardService.getDashboardForUser.
 */
@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private EntryRepository entryRepository;
    @Mock
    private AccountService accountService;
    @Mock
    private CurrentUserProvider currentUserProvider;

    private DashboardService service() {
        return new DashboardService(entryRepository, accountService, currentUserProvider);
    }

    private AppUser user() {
        return AppUser.builder().id(UUID.randomUUID()).email("marcio@example.com").name("Marcio").build();
    }

    private Entry entry(AppUser owner, EntryType type, EntryStatus status, BigDecimal amount, LocalDate dueDate) {
        return entry(owner, type, status, amount, dueDate, false);
    }

    private Entry entry(AppUser owner, EntryType type, EntryStatus status, BigDecimal amount, LocalDate dueDate,
                         boolean excludedFromTotals) {
        Category category = Category.builder().id(UUID.randomUUID()).owner(owner)
                .name(type == EntryType.RECEITA ? "Salário" : "Mercado")
                .type(type == EntryType.RECEITA ? CategoryType.RECEITA : CategoryType.DESPESA).active(true).build();
        return Entry.builder().id(UUID.randomUUID()).owner(owner).description("Lançamento")
                .amount(amount).dueDate(dueDate).type(type).status(status).origin(EntryOrigin.MANUAL)
                .category(category).excludedFromTotals(excludedFromTotals).build();
    }

    @Test
    void naoDeveContarReceitaJaRecebidaDeNovoNoSaldoProjetado() {
        // getDashboardForUser recebe o AppUser direto (ver javadoc de DashboardService), não
        // passa pelo CurrentUserProvider — por isso não há stub dele aqui.
        AppUser user = user();
        LocalDate today = LocalDate.of(2026, 9, 15);

        // Receita de R$1000 já recebida (PAGO) — já está refletida no saldo consolidado abaixo.
        Entry receitaPaga = entry(user, EntryType.RECEITA, EntryStatus.PAGO, new BigDecimal("1000.00"), today);
        // Receita de R$300 ainda não recebida — essa sim deve entrar na projeção.
        Entry receitaPendente = entry(user, EntryType.RECEITA, EntryStatus.PENDENTE, new BigDecimal("300.00"), today.plusDays(10));
        // Despesa de R$200 ainda não paga.
        Entry despesaPendente = entry(user, EntryType.DESPESA, EntryStatus.PENDENTE, new BigDecimal("200.00"), today.plusDays(5));

        when(entryRepository.findByOwnerIdAndDueDateBetweenOrderByDueDateAsc(eq(user.getId()), any(), any()))
                .thenReturn(List.of(receitaPaga, receitaPendente, despesaPendente));
        when(accountService.consolidatedBalance(user.getId())).thenReturn(new BigDecimal("5000.00"));

        DashboardResponse dashboard = service().getDashboardForUser(user, today);

        // 5000 (já inclui a receita paga) + 300 (receita pendente) - 200 (despesa pendente) = 5100.
        // Sem o fix, o cálculo antigo somaria também os 1000 já recebidos: daria 6100.
        assertThat(dashboard.projectedBalanceEndOfMonth()).isEqualByComparingTo("5100.00");
        assertThat(dashboard.totalIncomePaidMonth()).isEqualByComparingTo("1000.00");
        assertThat(dashboard.totalIncomePendingMonth()).isEqualByComparingTo("300.00");
    }

    @Test
    void naoDeveSomarLancamentoMarcadoComoExcludedFromTotals() {
        // Achado real (29/09, relatado pelo Marcio comparando com a planilha dele): o Dashboard
        // somava TODO lançamento do mês, inclusive os marcados excludedFromTotals (transferência
        // entre contas próprias, liquidação de fatura contada em duplicidade) — o totalizador de
        // Lançamentos já respeitava essa flag desde a quarta rodada, mas o Dashboard nunca tinha
        // sido atualizado junto, porque calcula os totais à mão a partir de outra query.
        AppUser user = user();
        LocalDate today = LocalDate.of(2026, 9, 15);

        Entry despesaNormal = entry(user, EntryType.DESPESA, EntryStatus.PAGO, new BigDecimal("100.00"), today);
        Entry transferenciaExcluida = entry(user, EntryType.DESPESA, EntryStatus.PAGO, new BigDecimal("5000.00"),
                today, true);
        Entry receitaExcluida = entry(user, EntryType.RECEITA, EntryStatus.PAGO, new BigDecimal("3000.00"),
                today, true);

        when(entryRepository.findByOwnerIdAndDueDateBetweenOrderByDueDateAsc(eq(user.getId()), any(), any()))
                .thenReturn(List.of(despesaNormal, transferenciaExcluida, receitaExcluida));
        when(accountService.consolidatedBalance(user.getId())).thenReturn(BigDecimal.ZERO);

        DashboardResponse dashboard = service().getDashboardForUser(user, today);

        assertThat(dashboard.totalPaidMonth()).isEqualByComparingTo("100.00");
        assertThat(dashboard.totalExpensesMonth()).isEqualByComparingTo("100.00");
        assertThat(dashboard.totalIncomePaidMonth()).isEqualByComparingTo("0.00");
    }

    @Test
    void totalExpensesMonthDeveSomarPagoEPendente() {
        AppUser user = user();
        LocalDate today = LocalDate.of(2026, 9, 15);

        Entry despesaPaga = entry(user, EntryType.DESPESA, EntryStatus.PAGO, new BigDecimal("150.00"), today);
        Entry despesaPendente = entry(user, EntryType.DESPESA, EntryStatus.PENDENTE, new BigDecimal("250.00"), today.plusDays(3));

        when(entryRepository.findByOwnerIdAndDueDateBetweenOrderByDueDateAsc(eq(user.getId()), any(), any()))
                .thenReturn(List.of(despesaPaga, despesaPendente));
        when(accountService.consolidatedBalance(user.getId())).thenReturn(BigDecimal.ZERO);

        DashboardResponse dashboard = service().getDashboardForUser(user, today);

        assertThat(dashboard.totalPaidMonth()).isEqualByComparingTo("150.00");
        assertThat(dashboard.totalPendingMonth()).isEqualByComparingTo("250.00");
        assertThat(dashboard.totalExpensesMonth()).isEqualByComparingTo("400.00");
    }
}
