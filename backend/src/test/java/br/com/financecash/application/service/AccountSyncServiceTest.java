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
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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
    @Mock
    private TransactionImportService transactionImportService;

    private AccountSyncService service() {
        return service(365);
    }

    private AccountSyncService service(int transactionLookbackDays) {
        return new AccountSyncService(accountRepository, balanceSnapshotRepository, bankConnectionRepository,
                creditCardRepository, pluggyClient, currentUserProvider, transactionImportService,
                transactionLookbackDays);
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
        // Guardado separado desde a migration V7 (01/10, quinta rodada) — permite montar um
        // nome curto de exibição ("C6 Mastercard") sem reprocessar CreditCard.name.
        assertThat(saved.getBrand()).isEqualTo("MASTERCARD");
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
        // Cartão sincronizado antes da migration V7 nasce com brand null — o próximo sync
        // preenche, mesmo em update (01/10, quinta rodada).
        assertThat(existing.getBrand()).isEqualTo("VISA");
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
    void deveSomarLancamentosImportadosDeContasECartoes() {
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
        lenient().when(creditCardRepository.save(any(CreditCard.class))).thenAnswer(invocation -> {
            CreditCard c = invocation.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });
        when(transactionImportService.importForAccount(any(), any(), org.mockito.ArgumentMatchers.eq("acc-1"), any(), any()))
                .thenReturn(2);
        when(transactionImportService.importForCreditCard(any(), any(), org.mockito.ArgumentMatchers.eq("acc-2"), any(), any()))
                .thenReturn(3);

        SyncResultDTO result = service().syncConnection(connection.getId());

        assertThat(result.entriesImported()).isEqualTo(5);
    }

    @Test
    void deveBuscarTransacoesSempreNaJanelaConfiguradaMesmoComSyncRecenteAnterior() {
        // Achado em produção (29/09): a janela de busca de transações não pode encolher com
        // base no lastSyncAt anterior — esse campo já vinha sendo preenchido desde antes do
        // TransactionImportService existir, então a janela nunca chegava a olhar pra trás o
        // suficiente pra pegar compras de cartão fora dos últimos dias. Trava que a janela é
        // sempre fixa (não encolhe), independente de quão recente foi o último sync.
        //
        // Usa um valor de lookback diferente do default (200, não 365) de propósito, pra provar
        // que a janela realmente vem da configuração (financecash.pluggy.sync-window-days, ver
        // AccountSyncService) e não de uma constante fixa — 120 dias era hardcoded antes, agora
        // é 365 por padrão mas configurável (1/10, a pedido do Marcio, pra dar mais histórico —
        // ver docs/ROADMAP.md).
        AppUser user = user();
        BankConnection connection = connection(user);
        connection.setLastSyncAt(Instant.now().minus(1, ChronoUnit.HOURS));

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

        service(200).syncConnection(connection.getId());

        var fromCaptor = org.mockito.ArgumentCaptor.forClass(LocalDate.class);
        var toCaptor = org.mockito.ArgumentCaptor.forClass(LocalDate.class);
        verify(transactionImportService).importForAccount(
                org.mockito.ArgumentMatchers.eq(user), any(), org.mockito.ArgumentMatchers.eq("acc-1"),
                fromCaptor.capture(), toCaptor.capture());
        assertThat(toCaptor.getValue()).isEqualTo(LocalDate.now());
        assertThat(fromCaptor.getValue()).isEqualTo(LocalDate.now().minusDays(200));
    }

    @Test
    void deveIgnorarContaDesativadaSemReativarOuAtualizarSaldo() {
        // Achado real (28/09, relatado pelo Marcio): ele desativou a conta 99Pay e sincronizou
        // de novo esperando que ela reaparecesse em Contas & Saldos, mas continuou sumida, com
        // o saldo antigo ainda visível em Lançamentos — a busca por banco+tipo não filtrava por
        // active, então achava e atualizava a conta desativada por baixo dos panos pra sempre.
        // Trava agora: uma vez desativada, o sync nunca mais toca na conta.
        AppUser user = user();
        BankConnection connection = connection(user);
        Account inactive = Account.builder().id(UUID.randomUUID()).owner(user).name("99Pay")
                .bankName("99Pay").type(AccountType.CORRENTE).active(false).build();

        when(currentUserProvider.getCurrentUser()).thenReturn(user);
        when(bankConnectionRepository.findById(connection.getId())).thenReturn(Optional.of(connection));
        when(pluggyClient.listAccounts("item-1")).thenReturn(List.of(
                bankAccount("acc-1", "CHECKING_ACCOUNT", "00074387-7", "99Pay", new BigDecimal("999.99"))
        ));
        when(accountRepository.findByOwnerIdAndBankNameIgnoreCaseAndType(user.getId(), "99Pay", AccountType.CORRENTE))
                .thenReturn(Optional.of(inactive));

        SyncResultDTO result = service().syncConnection(connection.getId());

        assertThat(result.accountsCreated()).isZero();
        assertThat(result.accountsUpdated()).isZero();
        assertThat(result.accountsSkipped()).isEqualTo(1);
        assertThat(inactive.isActive()).isFalse();
        verify(balanceSnapshotRepository, org.mockito.Mockito.never())
                .save(org.mockito.ArgumentMatchers.any());
        verify(transactionImportService, org.mockito.Mockito.never())
                .importForAccount(any(), any(), any(), any(), any());
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
