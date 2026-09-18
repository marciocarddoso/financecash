package br.com.finkeeper.application.service;

import br.com.finkeeper.domain.model.*;
import br.com.finkeeper.domain.repository.EntryRepository;
import br.com.finkeeper.domain.repository.RecurringRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecurringEntryGenerationServiceTest {

    @Mock
    private RecurringRuleRepository recurringRuleRepository;

    @Mock
    private EntryRepository entryRepository;

    private RecurringEntryGenerationService service;

    private AppUser user;
    private Category category;

    @BeforeEach
    void setUp() {
        service = new RecurringEntryGenerationService(recurringRuleRepository, entryRepository);
        user = AppUser.builder().id(UUID.randomUUID()).email("marcio@example.com").name("Marcio").passwordHash("x").build();
        category = Category.builder().id(UUID.randomUUID()).name("Salário").type(CategoryType.RECEITA).build();
    }

    @Test
    void deveGerarDozeLancamentosParaRegraMensalAoLongoDeUmAno() {
        RecurringRule salario = mensalRule(new BigDecimal("5000.00"), LocalDate.of(2026, 1, 1));

        when(recurringRuleRepository.findByOwnerIdAndActiveTrueOrderByNameAsc(user.getId()))
                .thenReturn(List.of(salario));
        when(entryRepository.findByOwnerIdAndRecurringRuleIdOrderByDueDateDesc(any(), any()))
                .thenReturn(List.of());

        int created = service.generateForUser(user, LocalDate.of(2026, 1, 5), 11);

        assertThat(created).isEqualTo(12);
        verify(entryRepository, times(12)).save(any(Entry.class));
    }

    @Test
    void naoDeveDuplicarLancamentoJaGeradoNoMesmoMes() {
        RecurringRule salario = mensalRule(new BigDecimal("5000.00"), LocalDate.of(2026, 1, 1));
        LocalDate referencia = LocalDate.of(2026, 3, 1);

        Entry jaExistente = Entry.builder()
                .id(UUID.randomUUID())
                .dueDate(LocalDate.of(2026, 3, 5))
                .build();

        when(recurringRuleRepository.findByOwnerIdAndActiveTrueOrderByNameAsc(user.getId()))
                .thenReturn(List.of(salario));
        when(entryRepository.findByOwnerIdAndRecurringRuleIdOrderByDueDateDesc(any(), any()))
                .thenReturn(List.of(jaExistente));

        int created = service.generateForUser(user, referencia, 0);

        assertThat(created).isZero();
        verify(entryRepository, never()).save(any(Entry.class));
    }

    @Test
    void deveUsarValorVigenteAposReajuste() {
        RecurringRule aluguel = mensalRule(new BigDecimal("1200.00"), LocalDate.of(2025, 1, 1));
        aluguel.getValueHistory().add(RecurringRuleValueHistory.builder()
                .recurringRule(aluguel)
                .effectiveFrom(LocalDate.of(2026, 6, 1))
                .amount(new BigDecimal("1350.00"))
                .build());

        when(recurringRuleRepository.findByOwnerIdAndActiveTrueOrderByNameAsc(user.getId()))
                .thenReturn(List.of(aluguel));
        when(entryRepository.findByOwnerIdAndRecurringRuleIdOrderByDueDateDesc(any(), any()))
                .thenReturn(List.of());

        service.generateForUser(user, LocalDate.of(2026, 6, 1), 0);

        ArgumentCaptor<Entry> captor = ArgumentCaptor.forClass(Entry.class);
        verify(entryRepository).save(captor.capture());
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo("1350.00");
    }

    private RecurringRule mensalRule(BigDecimal amount, LocalDate startDate) {
        RecurringRule rule = RecurringRule.builder()
                .id(UUID.randomUUID())
                .owner(user)
                .name("Salário")
                .type(EntryType.RECEITA)
                .category(category)
                .frequency(RecurrenceFrequency.MENSAL)
                .dayOfMonth(5)
                .referenceMonths(new ArrayList<>())
                .startDate(startDate)
                .active(true)
                .valueHistory(new ArrayList<>())
                .build();

        rule.getValueHistory().add(RecurringRuleValueHistory.builder()
                .recurringRule(rule)
                .effectiveFrom(startDate)
                .amount(amount)
                .build());

        return rule;
    }
}
