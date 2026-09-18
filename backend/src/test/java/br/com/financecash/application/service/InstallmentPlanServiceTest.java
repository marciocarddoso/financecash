package br.com.financecash.application.service;

import br.com.financecash.application.dto.InstallmentPlanCreateRequest;
import br.com.financecash.domain.model.*;
import br.com.financecash.domain.repository.*;
import br.com.financecash.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InstallmentPlanServiceTest {

    @Mock private InstallmentPlanRepository installmentPlanRepository;
    @Mock private EntryRepository entryRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private CreditCardRepository creditCardRepository;
    @Mock private CurrentUserProvider currentUserProvider;

    private InstallmentPlanService service;
    private AppUser user;
    private Category category;

    @BeforeEach
    void setUp() {
        service = new InstallmentPlanService(installmentPlanRepository, entryRepository, categoryRepository,
                accountRepository, creditCardRepository, currentUserProvider);
        user = AppUser.builder().id(UUID.randomUUID()).email("marcio@example.com").name("Marcio").passwordHash("x").build();
        category = Category.builder().id(UUID.randomUUID()).name("Compras").type(CategoryType.DESPESA).build();

        when(currentUserProvider.getCurrentUser()).thenReturn(user);
        when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
        when(installmentPlanRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void deveGerarUmaParcelaParaCadaMesEAbsorverArredondamentoNaUltima() {
        var request = new InstallmentPlanCreateRequest(
                "Notebook", new BigDecimal("1000.00"), 3, LocalDate.of(2026, 1, 10), category.getId(), null, null);

        service.create(request);

        ArgumentCaptor<Entry> captor = ArgumentCaptor.forClass(Entry.class);
        verify(entryRepository, times(3)).save(captor.capture());

        List<Entry> entries = captor.getAllValues();
        assertThat(entries).hasSize(3);
        assertThat(entries.get(0).getAmount()).isEqualByComparingTo("333.33");
        assertThat(entries.get(1).getAmount()).isEqualByComparingTo("333.33");
        assertThat(entries.get(2).getAmount()).isEqualByComparingTo("333.34"); // absorve o resto de 0,01

        BigDecimal total = entries.stream().map(Entry::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(total).isEqualByComparingTo("1000.00");

        assertThat(entries.get(0).getDueDate()).isEqualTo(LocalDate.of(2026, 1, 10));
        assertThat(entries.get(1).getDueDate()).isEqualTo(LocalDate.of(2026, 2, 10));
        assertThat(entries.get(2).getDueDate()).isEqualTo(LocalDate.of(2026, 3, 10));

        entries.forEach(e -> assertThat(e.getOrigin()).isEqualTo(EntryOrigin.PARCELAMENTO));
    }
}
