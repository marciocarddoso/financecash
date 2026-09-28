package br.com.financecash.application.service;

import br.com.financecash.application.dto.SyncResultDTO;
import br.com.financecash.domain.model.Account;
import br.com.financecash.domain.model.AccountType;
import br.com.financecash.domain.model.AppUser;
import br.com.financecash.domain.model.BalanceSnapshot;
import br.com.financecash.domain.model.BankConnection;
import br.com.financecash.domain.model.BankConnectionStatus;
import br.com.financecash.domain.repository.AccountRepository;
import br.com.financecash.domain.repository.BalanceSnapshotRepository;
import br.com.financecash.domain.repository.BankConnectionRepository;
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
    private PluggyClient pluggyClient;
    @Mock
    private CurrentUserProvider currentUserProvider;

    private AccountSyncService service() {
        return new AccountSyncService(accountRepository, balanceSnapshotRepository, bankConnectionRepository, pluggyClient, currentUserProvider);
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

    @Test
    void deveCriarContaNovaQuandoNaoExisteContaComEsseBancoETipo() {
        AppUser user = user();
        BankConnection connection = connection(user);
        when(currentUserProvider.getCurrentUser()).thenReturn(user);
        when(bankConnectionRepository.findById(connection.getId())).thenReturn(Optional.of(connection));
        when(pluggyClient.listAccounts("item-1")).thenReturn(List.of(
                new PluggyClient.AccountInfo("acc-1", "BANK", "CHECKING_ACCOUNT", "00074387-7", "itau", "Itaú",
                        new BigDecimal("100.00"), "BRL")
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
                new PluggyClient.AccountInfo("acc-2", "BANK", "CHECKING_ACCOUNT", "00165770-4", "Banco Bradesco", "Banco Bradesco",
                        new BigDecimal("250.50"), "BRL")
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
    void deveIgnorarContasDoTipoCredit() {
        AppUser user = user();
        BankConnection connection = connection(user);
        when(currentUserProvider.getCurrentUser()).thenReturn(user);
        when(bankConnectionRepository.findById(connection.getId())).thenReturn(Optional.of(connection));
        when(pluggyClient.listAccounts("item-1")).thenReturn(List.of(
                new PluggyClient.AccountInfo("acc-3", "CREDIT", "CREDIT_CARD", "4055", "BANDEIRADO", "BANDEIRADO",
                        new BigDecimal("18703.27"), "BRL")
        ));

        SyncResultDTO result = service().syncConnection(connection.getId());

        assertThat(result.accountsCreated()).isZero();
        assertThat(result.accountsUpdated()).isZero();
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
