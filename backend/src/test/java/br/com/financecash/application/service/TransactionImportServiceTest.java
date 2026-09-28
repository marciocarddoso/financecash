package br.com.financecash.application.service;

import br.com.financecash.domain.model.Account;
import br.com.financecash.domain.model.AccountType;
import br.com.financecash.domain.model.AppUser;
import br.com.financecash.domain.model.Category;
import br.com.financecash.domain.model.CategoryType;
import br.com.financecash.domain.model.CreditCard;
import br.com.financecash.domain.model.Entry;
import br.com.financecash.domain.model.EntryOrigin;
import br.com.financecash.domain.model.EntryStatus;
import br.com.financecash.domain.model.EntryType;
import br.com.financecash.domain.repository.CategoryRepository;
import br.com.financecash.domain.repository.EntryRepository;
import br.com.financecash.openfinance.PluggyClient;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionImportServiceTest {

    @Mock
    private PluggyClient pluggyClient;
    @Mock
    private EntryRepository entryRepository;
    @Mock
    private CategoryRepository categoryRepository;

    private TransactionImportService service() {
        return new TransactionImportService(pluggyClient, entryRepository, categoryRepository);
    }

    private AppUser user() {
        return AppUser.builder().id(UUID.randomUUID()).email("marcio@example.com").name("Marcio").build();
    }

    private Account account(AppUser owner) {
        return Account.builder().id(UUID.randomUUID()).owner(owner).name("Bradesco - Conta Corrente")
                .bankName("Banco Bradesco").type(AccountType.CORRENTE).active(true).build();
    }

    private CreditCard creditCard(AppUser owner) {
        return CreditCard.builder().id(UUID.randomUUID()).owner(owner).name("Bradesco - Cartão")
                .bankName("Banco Bradesco").closingDay(25).dueDay(5).active(true).build();
    }

    @Test
    void deveImportarTransacaoDeContaComoLancamentoJaPago() {
        AppUser user = user();
        Account account = account(user);
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 28);

        when(pluggyClient.listTransactions("acc-1", from, to)).thenReturn(List.of(
                new PluggyClient.TransactionInfo("tx-1", "Uber", new BigDecimal("-35.90"),
                        LocalDate.of(2026, 9, 14), "DEBIT", "POSTED", null)
        ));
        when(entryRepository.existsByOwnerIdAndDescriptionAndDueDateAndAmount(
                user.getId(), "Uber", LocalDate.of(2026, 9, 14), new BigDecimal("35.90"))).thenReturn(false);
        when(categoryRepository.findByOwnerIdAndNameIgnoreCase(user.getId(), "Outros")).thenReturn(Optional.empty());
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        int imported = service().importForAccount(user, account, "acc-1", from, to);

        assertThat(imported).isEqualTo(1);
        ArgumentCaptor<Entry> captor = ArgumentCaptor.forClass(Entry.class);
        verify(entryRepository).save(captor.capture());
        Entry saved = captor.getValue();
        assertThat(saved.getDescription()).isEqualTo("Uber");
        assertThat(saved.getAmount()).isEqualByComparingTo("35.90");
        assertThat(saved.getType()).isEqualTo(EntryType.DESPESA);
        assertThat(saved.getStatus()).isEqualTo(EntryStatus.PAGO);
        assertThat(saved.getOrigin()).isEqualTo(EntryOrigin.IMPORTADO_EXTRATO);
        assertThat(saved.getDueDate()).isEqualTo(LocalDate.of(2026, 9, 14));
        assertThat(saved.getPaymentDate()).isEqualTo(LocalDate.of(2026, 9, 14));
        assertThat(saved.getAccount()).isEqualTo(account);
        assertThat(saved.getCreditCard()).isNull();
    }

    @Test
    void deveMarcarComoReceitaQuandoAmountPositivoNumaConta() {
        AppUser user = user();
        Account account = account(user);
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 28);

        when(pluggyClient.listTransactions("acc-1", from, to)).thenReturn(List.of(
                new PluggyClient.TransactionInfo("tx-2", "Salário", new BigDecimal("5000.00"),
                        LocalDate.of(2026, 9, 5), "CREDIT", "POSTED", "Salário")
        ));
        when(categoryRepository.findByOwnerIdAndNameIgnoreCase(user.getId(), "Salário")).thenReturn(Optional.empty());
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        service().importForAccount(user, account, "acc-1", from, to);

        ArgumentCaptor<Entry> captor = ArgumentCaptor.forClass(Entry.class);
        verify(entryRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(EntryType.RECEITA);

        ArgumentCaptor<Category> categoryCaptor = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository).save(categoryCaptor.capture());
        assertThat(categoryCaptor.getValue().getName()).isEqualTo("Salário");
        assertThat(categoryCaptor.getValue().getType()).isEqualTo(CategoryType.RECEITA);
    }

    @Test
    void devePularTransacaoPendingDeConta() {
        AppUser user = user();
        Account account = account(user);
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 28);

        when(pluggyClient.listTransactions("acc-1", from, to)).thenReturn(List.of(
                new PluggyClient.TransactionInfo("tx-3", "Compra em análise", new BigDecimal("-10.00"),
                        LocalDate.of(2026, 9, 20), "DEBIT", "PENDING", null)
        ));

        int imported = service().importForAccount(user, account, "acc-1", from, to);

        assertThat(imported).isZero();
        verify(entryRepository, never()).save(any());
    }

    @Test
    void devePularTransacaoDeContaJaImportadaAntes() {
        AppUser user = user();
        Account account = account(user);
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 28);

        when(pluggyClient.listTransactions("acc-1", from, to)).thenReturn(List.of(
                new PluggyClient.TransactionInfo("tx-4", "Mercado", new BigDecimal("-150.00"),
                        LocalDate.of(2026, 9, 10), "DEBIT", "POSTED", null)
        ));
        when(entryRepository.existsByOwnerIdAndDescriptionAndDueDateAndAmount(
                user.getId(), "Mercado", LocalDate.of(2026, 9, 10), new BigDecimal("150.00"))).thenReturn(true);

        int imported = service().importForAccount(user, account, "acc-1", from, to);

        assertThat(imported).isZero();
        verify(entryRepository, never()).save(any());
        verify(categoryRepository, never()).findByOwnerIdAndNameIgnoreCase(any(), anyString());
    }

    @Test
    void deveImportarCompraDeCartaoComoLancamentoPendenteUsandoDueDateDaFatura() {
        AppUser user = user();
        CreditCard card = creditCard(user); // closingDay=25, dueDay=5
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 28);

        when(pluggyClient.listTransactions("acc-2", from, to)).thenReturn(List.of(
                new PluggyClient.TransactionInfo("tx-5", "Netflix", new BigDecimal("39.90"),
                        LocalDate.of(2026, 9, 14), "DEBIT", "POSTED", null)
        ));
        lenient().when(categoryRepository.findByOwnerIdAndNameIgnoreCase(user.getId(), "Outros")).thenReturn(Optional.empty());
        lenient().when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        int imported = service().importForCreditCard(user, card, "acc-2", from, to);

        assertThat(imported).isEqualTo(1);
        ArgumentCaptor<Entry> captor = ArgumentCaptor.forClass(Entry.class);
        verify(entryRepository).save(captor.capture());
        Entry saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(EntryStatus.PENDENTE);
        assertThat(saved.getOrigin()).isEqualTo(EntryOrigin.IMPORTADO_CARTAO);
        assertThat(saved.getDueDate()).isEqualTo(LocalDate.of(2026, 10, 5));
        assertThat(saved.getPaymentDate()).isNull();
        assertThat(saved.getCreditCard()).isEqualTo(card);
        assertThat(saved.getAccount()).isNull();
    }

    @Test
    void devePularPagamentoDeFaturaDeCartao() {
        AppUser user = user();
        CreditCard card = creditCard(user);
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 28);

        when(pluggyClient.listTransactions("acc-2", from, to)).thenReturn(List.of(
                new PluggyClient.TransactionInfo("tx-6", "Pagamento de fatura", new BigDecimal("-1200.00"),
                        LocalDate.of(2026, 9, 5), "CREDIT", "POSTED", null)
        ));

        int imported = service().importForCreditCard(user, card, "acc-2", from, to);

        assertThat(imported).isZero();
        verify(entryRepository, never()).save(any());
    }
}
