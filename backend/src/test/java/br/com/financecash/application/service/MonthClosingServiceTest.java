package br.com.financecash.application.service;

import br.com.financecash.application.dto.MonthClosingResponse;
import br.com.financecash.domain.model.AppUser;
import br.com.financecash.domain.model.Category;
import br.com.financecash.domain.model.Entry;
import br.com.financecash.domain.model.EntryStatus;
import br.com.financecash.domain.model.EntryType;
import br.com.financecash.domain.repository.EntryRepository;
import br.com.financecash.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MonthClosingServiceTest {

    @Mock
    private EntryRepository entryRepository;
    @Mock
    private CurrentUserProvider currentUserProvider;

    private MonthClosingService service;
    private AppUser user;
    private Category mercado;

    @BeforeEach
    void setUp() {
        service = new MonthClosingService(entryRepository, currentUserProvider);
        user = AppUser.builder().id(UUID.randomUUID()).build();
        mercado = Category.builder().id(UUID.randomUUID()).name("Mercado").colorHex("#ff0000").build();
        when(currentUserProvider.getCurrentUser()).thenReturn(user);
    }

    private Entry entry(EntryType type, EntryStatus status, String amount, Category category) {
        return Entry.builder()
                .id(UUID.randomUUID())
                .description("teste")
                .amount(new BigDecimal(amount))
                .dueDate(LocalDate.of(2026, 9, 10))
                .type(type)
                .status(status)
                .category(category)
                .build();
    }

    @Test
    void deveSepararPrevistoDeRealizadoIgnorandoCancelados() {
        when(entryRepository.findByOwnerIdAndDueDateBetweenOrderByDueDateAsc(any(), any(), any()))
                .thenReturn(List.of(
                        entry(EntryType.DESPESA, EntryStatus.PAGO, "100.00", mercado),
                        entry(EntryType.DESPESA, EntryStatus.PENDENTE, "50.00", mercado),
                        entry(EntryType.DESPESA, EntryStatus.CANCELADO, "999.00", mercado),
                        entry(EntryType.RECEITA, EntryStatus.PAGO, "4450.00", mercado)
                ));

        MonthClosingResponse response = service.getMonthClosing(2026, 9);

        assertThat(response.previstoDespesa()).isEqualByComparingTo("150.00");
        assertThat(response.realizadoDespesa()).isEqualByComparingTo("100.00");
        assertThat(response.previstoReceita()).isEqualByComparingTo("4450.00");
        assertThat(response.realizadoReceita()).isEqualByComparingTo("4450.00");
    }

    @Test
    void deveAgruparPorCategoria() {
        when(entryRepository.findByOwnerIdAndDueDateBetweenOrderByDueDateAsc(any(), any(), any()))
                .thenReturn(List.of(
                        entry(EntryType.DESPESA, EntryStatus.PAGO, "30.00", mercado),
                        entry(EntryType.DESPESA, EntryStatus.PENDENTE, "20.00", mercado)
                ));

        MonthClosingResponse response = service.getMonthClosing(2026, 9);

        assertThat(response.categorias()).hasSize(1);
        assertThat(response.categorias().get(0).categoryName()).isEqualTo("Mercado");
        assertThat(response.categorias().get(0).previsto()).isEqualByComparingTo("50.00");
        assertThat(response.categorias().get(0).realizado()).isEqualByComparingTo("30.00");
    }
}
