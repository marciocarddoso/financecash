package br.com.financecash.application.service;

import br.com.financecash.application.dto.SyncResultDTO;
import br.com.financecash.domain.model.Account;
import br.com.financecash.domain.model.AccountType;
import br.com.financecash.domain.model.AppUser;
import br.com.financecash.domain.model.BalanceSnapshot;
import br.com.financecash.domain.model.BankConnection;
import br.com.financecash.domain.model.BankConnectionStatus;
import br.com.financecash.domain.model.CreditCard;
import br.com.financecash.domain.repository.AccountRepository;
import br.com.financecash.domain.repository.BalanceSnapshotRepository;
import br.com.financecash.domain.repository.BankConnectionRepository;
import br.com.financecash.domain.repository.CreditCardRepository;
import br.com.financecash.exception.ResourceNotFoundException;
import br.com.financecash.openfinance.PluggyClient;
import br.com.financecash.security.CurrentUserProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountSyncServiceTest {

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private BalanceSnapshotRepository balanceSnapshotRepository;
    @Mock
    private BankConnectionRepository bankConnectionRepository;
    @Mock
    private CreditCardRepository creditCardRepository;
    @Mock
    private PluggyClient pluggyClient;
    @Mock
    private CurrentUserProvider currentUserProvider;

    private AccountSyncService service() {
        return new AccountSyncService(accountRepository, balanceSnapshotRepository, bankConnectionRepository,
                creditCardRepository, pluggyClient, currentUserProvider);
    }

    private AppUser user() {
        return AppUser.builder().id(UUID.randomUUID()).email("marcio@example.com").name("Marcio").build();
    }

    private BankConnection connection(AppUser owner) {
        return BankConnection.builder()
                .id(UUID.randomUUID())
                .owner(owner)
                .itemId("item-1")
                .bankName("MeuPluggy")
                .status(BankConnectionStatus.ATIVA)
                .build();
    }

    private PluggyClient.AccountInfo bankAccount(String id, String subtype, String number, String bankName, BigDecimal balance) {
        return new PluggyClient.AccountInfo(id, "BANK", subtype, number, bankName, bankName, balance, "BRL", null);
    }

    private PluggyClient.AccountInfo creditAccount(String id, String number, String genericName, BigDecimal balance,
                                                     PluggyClient.CreditDataInfo creditData) {
        return new PluggyClient.AccountInfo(id, "CREDIT", "CREDIT_CARD", number, genericName, genericName, balance, "BRL", creditData);
    }

    @Test
    void deveCriarContaNovaQuandoNaoExisteContaComEsseBancoETipo() {
        AppUser user = user();
        BankConnection connection = connection(user);
        when(currentUserProvider.getCurrentUser()).thenReturn(user);
        when(bankConnectionRepository.findById(connection.getId())).thenReturn(Optional.of(connection));
        when(pluggyClient.listAccounts("item-1")).thenReturn(List.of(
                bankAccount("acc-1", "CHECKING_ACCOUNT", "00074387-7", "Itaú", new BigDecimal("100.00"))
        ));
        when(accountRepository.findByOwnerIdAndBankNameIgnoreCaseAndType(user.getId(), "Itaú", AccountType.CORRENTE))
                .thenReturn(Optional.empty());
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> {
            Account a = invocation.getArgument(0);
            a.setId(UUID.randomUUID());
            return a;
        });
        when(balanceSnapshotRepository.findByAccountIdAndReferenceDate(any(), any())).thenReturn(Optional.empty());

        SyncResultDTO result = service().syncConnection(connection.getId());

        assertThat(result.accountsCreated()).isEqualTo(1);
        assertThat(result.accountsUpdated()).isZero();
        assertThat(result.creditCardsSkipped()).isZero();
        verify(bankConnectionRepository).save(connection);
        assertThat(connection.getLastSyncAt()).isNotNull();
    }

    @Test
    void deveSubstituirSaldoDoDiaQuandoContaJaExiste() {
        AppUser user = user();
        BankConnection connection = connection(user);
        Account existing = Account.builder().id(UUID.randomUUID()).owner(user).name("Bradesco - Conta Corrente")
                .bankName("Banco Bradesco").type(AccountType.CORRENTE).active(true).build();
        BalanceSnapshot existingSnapshot = BalanceSnapshot.builder().id(UUID.randomUUID()).account(existing)
                .referenceDate(LocalDate.now()).balance(new BigDecimal("10.00")).build();

        when(currentUserProvider.getCurrentUser()).thenReturn(user);
        when(bankConnectionRepository.findById(connection.getId())).thenReturn(Optional.of(connection));
        when(pluggyClient.listAccounts("item-1")).thenReturn(List.of(
                bankAccount("acc-2", "CHECKING_ACCOUNT", "00165770-4", "Banco Bradesco", new BigDecimal("250.50"))
        ));
        when(accountRepository.findByOwnerIdAndBankNameIgnoreCaseAndType(user.getId(), "Banco Bradesco", AccountType.CORRENTE))
                .thenReturn(Optional.of(existing));
        when(balanceSnapshotRepository.findByAccountIdAndReferenceDate(existing.getId(), LocalDate.now()))
                .thenReturn(Optional.of(existingSnapshot));

        SyncResultDTO result = service().syncConnection(connection.getId());

        assertThat(result.accountsCreated()).isZero();
        assertThat(result.accountsUpdated()).isEqualTo(1);
        assertThat(existingSnapshot.getBalance()).isEqualByComparingTo("250.50");
        verify(balanceSnapshotRepository).save(existingSnapshot);
    }

    @Test
    void deveCriarCartaoNovoUsandoNomeDoBancoDaContaBankDaMesmaConexao() {
        AppUser user = user();
        BankConnection connection = connection(user);
        var creditData = new PluggyClient.CreditDataInfo("MASTERCARD", LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 20),
                new BigDecimal("20000.00"), new BigDecimal("1296.73"));

        when(currentUserProvider.getCurrentUser()).thenReturn(user);
        when(bankConnectionRepository.findById(connection.getId())).thenReturn(Optional.of(connection));
        when(pluggyClient.listAccounts("item-1")).thenReturn(List.of(
                bankAccount("acc-1", "CHECKING_ACCOUNT", "31350519-5", "C6 BANK", new BigDecimal("0.00")),
                creditAccount("acc-2", "4055", "BANDEIRADO", new BigDecimal("18703.27"), creditData)
        ));
        lenient().when(accountRepository.findByOwnerIdAndBankNameIgnoreCaseAndType(any(), any(), any())).thenReturn(Optional.empty());
        lenient().when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> {
            Account a = invocation.getArgument(0);
            a.setId(UUID.randomUUID());
            return a;
        });
        lenient().when(balanceSnapshotRepository.findByAccountIdAndReferenceDate(any(), any())).thenReturn(Optional.empty());
        when(creditCardRepository.findByOwnerIdAndBankNameIgnoreCase(user.getId(), "C6 BANK")).thenReturn(Optional.empty());

        SyncResultDTO result = service().syncConnection(connection.getId());

        assertThat(result.creditCardsCreated()).isEqualTo(1);
        assertThat(result.creditCardsUpdated()).isZero();
        assertThat(result.creditCardsSkipped()).isZero();

        var captor = org.mockito.ArgumentCaptor.forClass(CreditCard.class);
        verify(creditCardRepository).save(captor.capture());
        CreditCard saved = captor.getValue();
        assertThat(saved.getBankName()).isEqualTo("C6 BANK");
        assertThat(saved.getClosingDay()).isEqualTo(10);
        assertThat(saved.getDueDay()).isEqualTo(20);
        assertThat(saved.getName()).contains("MASTERCARD");
    }

    @Test
    void deveAtualizarDiaDeFechamentoEVencimentoDeCartaoJaExistente() {
        AppUser user = user();
        BankConnection connection = connection(user);
        CreditCard existing = CreditCard.builder().id(UUID.randomUUID()).owner(user).name("Banco Bradesco - Cartão")
                .bankName("Banco Bradesco").closingDay(1).dueDay(8).active(true).build();
        var creditData = new PluggyClient.CreditDataInfo("VISA", LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 14),
                new BigDecimal("15000.00"), new BigDecimal("7139.91"));

        when(currentUserProvider.getCurrentUser()).thenReturn(user);
        when(bankConnectionRepository.findById(connection.getId())).thenReturn(Optional.of(connection));
        when(pluggyClient.listAccounts("item-1")).thenReturn(List.of(
                bankAccount("acc-1", "CHECKING_ACCOUNT", "00165770-4", "Banco Bradesco", new BigDecimal("0.00")),
                creditAccount("acc-2", "7707", "OUTROS", new BigDecimal("7860.09"), creditData)
        ));
        lenient().when(accountRepository.findByOwnerIdAndBankNameIgnoreCaseAndType(any(), any(), any())).thenReturn(Optional.empty());
        lenient().when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> {
            Account a = invocation.getArgument(0);
            a.setId(UUID.randomUUID());
            return a;
        });
        lenient().when(balanceSnapshotRepository.findByAccountIdAndReferenceDate(any(), any())).thenReturn(Optional.empty());
        when(creditCardRepository.findByOwnerIdAndBankNameIgnoreCase(user.getId(), "Banco Bradesco")).thenReturn(Optional.of(existing));

        SyncResultDTO result = service().syncConnection(connection.getId());

        assertThat(result.creditCardsCreated()).isZero();
        assertThat(result.creditCardsUpdated()).isEqualTo(1);
        assertThat(existing.getClosingDay()).isEqualTo(7);
        assertThat(existing.getDueDay()).isEqualTo(14);
    }

    @Test
    void deveEstimarDiaDeFechamentoQuandoPluggyNaoInformaBalanceCloseDate() {
        // Cenário real confirmado em produção: a Pluggy manda balanceDueDate mas não manda
        // balanceCloseDate pros conectores MeuPluggy (Bradesco, C6, Nubank testados) — o
        // cartão não pode ficar de fora só por causa disso, mas o closingDay é uma estimativa.
        AppUser user = user();
        BankConnection connection = connection(user);
        var creditData = new PluggyClient.CreditDataInfo("VISA", null, LocalDate.of(2026, 9, 10),
                new BigDecimal("21600.00"), new BigDecimal("13739.91"));

        when(currentUserProvider.getCurrentUser()).thenReturn(user);
        when(bankConnectionRepository.findById(connection.getId())).thenReturn(Optional.of(connection));
        when(pluggyClient.listAccounts("item-1")).thenReturn(List.of(
                bankAccount("acc-1", "CHECKING_ACCOUNT", "00165770-4", "Banco Bradesco", new BigDecimal("0.00")),
                creditAccount("acc-2", "7707", "OUTROS", new BigDecimal("7860.09"), creditData)
        ));
        lenient().when(accountRepository.findByOwnerIdAndBankNameIgnoreCaseAndType(any(), any(), any())).thenReturn(Optional.empty());
        lenient().when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> {
            Account a = invocation.getArgument(0);
            a.setId(UUID.randomUUID());
            return a;
        });
        lenient().when(balanceSnapshotRepository.findByAccountIdAndReferenceDate(any(), any())).thenReturn(Optional.empty());
        when(creditCardRepository.findByOwnerIdAndBankNameIgnoreCase(user.getId(), "Banco Bradesco")).thenReturn(Optional.empty());

        SyncResultDTO result = service().syncConnection(connection.getId());

        assertThat(result.creditCardsCreated()).isEqualTo(1);
        assertThat(result.creditCardsSkipped()).isZero();
        assertThat(result.creditCardsWithEstimatedClosingDay()).isEqualTo(1);

        var captor = org.mockito.ArgumentCaptor.forClass(CreditCard.class);
        verify(creditCardRepository).save(captor.capture());
        assertThat(captor.getValue().getDueDay()).isEqualTo(10);
    }

    @Test
    void naoDeveSobrescreverClosingDayExistenteQuandoPluggyNaoInformaBalanceCloseDate() {
        AppUser user = user();
        BankConnection connection = connection(user);
        CreditCard existing = CreditCard.builder().id(UUID.randomUUID()).owner(user).name("Banco Bradesco - Cartão")
                .bankName("Banco Bradesco").closingDay(5).dueDay(9).active(true).build();
        var creditData = new PluggyClient.CreditDataInfo("VISA", null, LocalDate.of(2026, 9, 10),
                new BigDecimal("21600.00"), new BigDecimal("13739.91"));

        when(currentUserProvider.getCurrentUser()).thenReturn(user);
        when(bankConnectionRepository.findById(connection.getId())).thenReturn(Optional.of(connection));
        when(pluggyClient.listAccounts("item-1")).thenReturn(List.of(
                bankAccount("acc-1", "CHECKING_ACCOUNT", "00165770-4", "Banco Bradesco", new BigDecimal("0.00")),
                creditAccount("acc-2", "7707", "OUTROS", new BigDecimal("7860.09"), creditData)
        ));
        lenient().when(accountRepository.findByOwnerIdAndBankNameIgnoreCaseAndType(any(), any(), any())).thenReturn(Optional.empty());
        lenient().when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> {
            Account a = invocation.getArgument(0);
            a.setId(UUID.randomUUID());
            return a;
        });
        lenient().when(balanceSnapshotRepository.findByAccountIdAndReferenceDate(any(), any())).thenReturn(Optional.empty());
        when(creditCardRepository.findByOwnerIdAndBankNameIgnoreCase(user.getId(), "Banco Bradesco")).thenReturn(Optional.of(existing));

        SyncResultDTO result = service().syncConnection(connection.getId());

        assertThat(result.creditCardsUpdated()).isEqualTo(1);
        assertThat(existing.getClosingDay()).isEqualTo(5);
        assertThat(existing.getDueDay()).isEqualTo(10);
    }

    @Test
    void deveIgnorarCartaoQuandoConexaoNaoTemContaBankPraIdentificarOBanco() {
        AppUser user = user();
        BankConnection connection = connection(user);
        var creditData = new PluggyClient.CreditDataInfo("VISA", LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 14),
                new BigDecimal("15000.00"), new BigDecimal("7139.91"));

        when(currentUserProvider.getCurrentUser()).thenReturn(user);
        when(bankConnectionRepository.findById(connection.getId())).thenReturn(Optional.of(connection));
        when(pluggyClient.listAccounts("item-1")).thenReturn(List.of(
                creditAccount("acc-2", "7707", "OUTROS", new BigDecimal("7860.09"), creditData)
        ));

        SyncResultDTO result = service().syncConnection(connection.getId());

        assertThat(result.creditCardsCreated()).isZero();
        assertThat(result.creditCardsUpdated()).isZero();
        assertThat(result.creditCardsSkipped()).isEqualTo(1);
    }

    @Test
    void deveIgnorarCartaoSemCreditData() {
        AppUser user = user();
        BankConnection connection = connection(user);

        when(currentUserProvider.getCurrentUser()).thenReturn(user);
        when(bankConnectionRepository.findById(connection.getId())).thenReturn(Optional.of(connection));
        when(pluggyClient.listAccounts("item-1")).thenReturn(List.of(
                bankAccount("acc-1", "CHECKING_ACCOUNT", "31350519-5", "C6 BANK", new BigDecimal("0.00")),
                creditAccount("acc-2", "4055", "BANDEIRADO", new BigDecimal("18703.27"), null)
        ));
        lenient().when(accountRepository.findByOwnerIdAndBankNameIgnoreCaseAndType(any(), any(), any())).thenReturn(Optional.empty());
        lenient().when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> {
            Account a = invocation.getArgument(0);
            a.setId(UUID.randomUUID());
            return a;
        });
        lenient().when(balanceSnapshotRepository.findByAccountIdAndReferenceDate(any(), any())).thenReturn(Optional.empty());

        SyncResultDTO result = service().syncConnection(connection.getId());

        assertThat(result.creditCardsCreated()).isZero();
        assertThat(result.creditCardsUpdated()).isZero();
        assertThat(result.creditCardsSkipped()).isEqualTo(1);
    }

    @Test
    void deveRecusarSincronizarConexaoDeOutroUsuario() {
        AppUser dono = user();
        AppUser outroUsuario = user();
        BankConnection connection = connection(dono);
        when(currentUserProvider.getCurrentUser()).thenReturn(outroUsuario);
        when(bankConnectionRepository.findById(connection.getId())).thenReturn(Optional.of(connection));

        assertThatThrownBy(() -> service().syncConnection(connection.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
