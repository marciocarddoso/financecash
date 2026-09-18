package br.com.financecash.application.service;

import br.com.financecash.application.dto.EntryImportSummary;
import br.com.financecash.domain.model.AppUser;
import br.com.financecash.domain.model.Category;
import br.com.financecash.domain.model.CategoryType;
import br.com.financecash.domain.model.Entry;
import br.com.financecash.domain.repository.CategoryRepository;
import br.com.financecash.domain.repository.EntryRepository;
import br.com.financecash.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntryImportServiceTest {

    @Mock
    private EntryRepository entryRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private CurrentUserProvider currentUserProvider;

    private EntryImportService service;
    private AppUser user;

    @BeforeEach
    void setUp() {
        service = new EntryImportService(entryRepository, categoryRepository, currentUserProvider);
        user = AppUser.builder().id(UUID.randomUUID()).build();
        when(currentUserProvider.getCurrentUser()).thenReturn(user);
    }

    private MultipartFile csv(String content) {
        return new MockMultipartFile("file", "entries.csv", "text/csv",
                content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void deveImportarLinhasValidasCriandoCategoriaQuandoNecessario() {
        String content = """
                description,amount,dueDate,type,categoryName,origin
                Bradesco - Mercado,129.90,2026-10-10,DESPESA,Cartão de Crédito - Bradesco,IMPORTADO_CARTAO
                Salário,4450.00,2026-10-05,RECEITA,Salário,
                """;

        when(entryRepository.existsByOwnerIdAndDescriptionAndDueDateAndAmount(any(), anyString(), any(), any()))
                .thenReturn(false);
        when(categoryRepository.findByOwnerIdAndNameIgnoreCase(eq(user.getId()), anyString()))
                .thenReturn(Optional.empty());
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));
        when(entryRepository.save(any(Entry.class))).thenAnswer(inv -> inv.getArgument(0));

        EntryImportSummary summary = service.importCsv(csv(content));

        assertThat(summary.imported()).isEqualTo(2);
        assertThat(summary.duplicates()).isZero();
        assertThat(summary.errors()).isEmpty();
        verify(entryRepository, org.mockito.Mockito.times(2)).save(any(Entry.class));
    }

    @Test
    void deveDefaultarOriginParaManualQuandoColunaVazia() {
        String content = """
                description,amount,dueDate,type,categoryName,origin
                Presente aniversário,50.00,2026-11-01,DESPESA,Outros,
                """;

        when(entryRepository.existsByOwnerIdAndDescriptionAndDueDateAndAmount(any(), anyString(), any(), any()))
                .thenReturn(false);
        when(categoryRepository.findByOwnerIdAndNameIgnoreCase(any(), anyString()))
                .thenReturn(Optional.of(Category.builder().id(UUID.randomUUID()).owner(user).name("Outros")
                        .type(CategoryType.DESPESA).active(true).build()));
        when(entryRepository.save(any(Entry.class))).thenAnswer(inv -> inv.getArgument(0));

        service.importCsv(csv(content));

        org.mockito.ArgumentCaptor<Entry> captor = org.mockito.ArgumentCaptor.forClass(Entry.class);
        verify(entryRepository).save(captor.capture());
        assertThat(captor.getValue().getOrigin().name()).isEqualTo("MANUAL");
    }

    @Test
    void devePularLinhaJaImportadaComoDuplicata() {
        String content = """
                description,amount,dueDate,type,categoryName,origin
                Bradesco - Mercado,129.90,2026-10-10,DESPESA,Cartão de Crédito - Bradesco,IMPORTADO_CARTAO
                """;

        when(entryRepository.existsByOwnerIdAndDescriptionAndDueDateAndAmount(
                eq(user.getId()), eq("Bradesco - Mercado"), eq(LocalDate.of(2026, 10, 10)), eq(new BigDecimal("129.90"))))
                .thenReturn(true);

        EntryImportSummary summary = service.importCsv(csv(content));

        assertThat(summary.imported()).isZero();
        assertThat(summary.duplicates()).isEqualTo(1);
        verify(entryRepository, never()).save(any());
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void deveRegistrarErroSemInterromperImportacaoDasDemaisLinhas() {
        String content = """
                description,amount,dueDate,type,categoryName,origin
                Linha com valor inválido,abc,2026-10-10,DESPESA,Outros,
                Bradesco - Mercado,129.90,2026-10-10,DESPESA,Cartão de Crédito - Bradesco,IMPORTADO_CARTAO
                """;

        when(entryRepository.existsByOwnerIdAndDescriptionAndDueDateAndAmount(any(), anyString(), any(), any()))
                .thenReturn(false);
        when(categoryRepository.findByOwnerIdAndNameIgnoreCase(any(), anyString()))
                .thenReturn(Optional.of(Category.builder().id(UUID.randomUUID()).owner(user).name("Outros")
                        .type(CategoryType.DESPESA).active(true).build()));
        when(entryRepository.save(any(Entry.class))).thenAnswer(inv -> inv.getArgument(0));

        EntryImportSummary summary = service.importCsv(csv(content));

        assertThat(summary.imported()).isEqualTo(1);
        assertThat(summary.errors()).hasSize(1);
        assertThat(summary.errors().get(0).line()).isEqualTo(2);
        assertThat(summary.errors().get(0).message()).contains("amount");
    }
}
