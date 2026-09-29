package br.com.financecash.application.service;

import br.com.financecash.application.dto.AccountUpdateRequest;
import br.com.financecash.application.dto.BalanceSnapshotCreateRequest;
import br.com.financecash.domain.model.Account;
import br.com.financecash.domain.model.AccountType;
import br.com.financecash.domain.model.AppUser;
import br.com.financecash.domain.repository.AccountRepository;
import br.com.financecash.domain.repository.BalanceSnapshotRepository;
import br.com.financecash.exception.BusinessException;
import br.com.financecash.exception.ResourceNotFoundException;
import br.com.financecash.security.CurrentUserProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private BalanceSnapshotRepository balanceSnapshotRepository;
    @Mock
    private CurrentUserProvider currentUserProvider;

    private AccountService service() {
        return new AccountService(accountRepository, balanceSnapshotRepository, currentUserProvider);
    }

    private AppUser user() {
        return AppUser.builder().id(UUID.randomUUID()).email("marcio@example.com").name("Marcio").build();
    }

    @Test
    void deveDesativarContaDoProprioUsuario() {
        AppUser user = user();
        UUID accountId = UUID.randomUUID();
        Account account = Account.builder().id(accountId).owner(user).name("Nubank").bankName("Nubank")
                .type(AccountType.CORRENTE).active(true).build();
        when(currentUserProvider.getCurrentUser()).thenReturn(user);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        service().deactivate(accountId);

        assertThat(account.isActive()).isFalse();
    }

    @Test
    void deveRecusarDesativarContaDeOutroUsuario() {
        AppUser dono = user();
        AppUser outroUsuario = user();
        UUID accountId = UUID.randomUUID();
        Account account = Account.builder().id(accountId).owner(dono).name("Nubank").bankName("Nubank")
                .type(AccountType.CORRENTE).active(true).build();
        when(currentUserProvider.getCurrentUser()).thenReturn(outroUsuario);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service().deactivate(accountId))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void deveLancarNotFoundAoDesativarContaInexistente() {
        UUID accountId = UUID.randomUUID();
        when(accountRepository.findById(accountId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().deactivate(accountId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deveReativarContaDoProprioUsuario() {
        AppUser user = user();
        UUID accountId = UUID.randomUUID();
        Account account = Account.builder().id(accountId).owner(user).name("Nubank").bankName("Nubank")
                .type(AccountType.CORRENTE).active(false).build();
        when(currentUserProvider.getCurrentUser()).thenReturn(user);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        service().activate(accountId);

        assertThat(account.isActive()).isTrue();
    }

    @Test
    void deveRecusarEditarContaSincronizadaPelaPluggy() {
        AppUser user = user();
        UUID accountId = UUID.randomUUID();
        Account account = Account.builder().id(accountId).owner(user).name("Nubank").bankName("Nubank")
                .type(AccountType.CORRENTE).active(true).syncedFromOpenFinance(true).build();
        when(currentUserProvider.getCurrentUser()).thenReturn(user);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        AccountUpdateRequest request = new AccountUpdateRequest("Novo nome", "Nubank", AccountType.CORRENTE, null);

        assertThatThrownBy(() -> service().update(accountId, request))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void devePermitirEditarContaManual() {
        AppUser user = user();
        UUID accountId = UUID.randomUUID();
        Account account = Account.builder().id(accountId).owner(user).name("Nubank").bankName("Nubank")
                .type(AccountType.CORRENTE).active(true).syncedFromOpenFinance(false).build();
        when(currentUserProvider.getCurrentUser()).thenReturn(user);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(accountRepository.save(account)).thenReturn(account);

        AccountUpdateRequest request = new AccountUpdateRequest("Novo nome", "Nubank", AccountType.CORRENTE, null);
        service().update(accountId, request);

        assertThat(account.getName()).isEqualTo("Novo nome");
    }

    @Test
    void deveRecusarSaldoManualEmContaSincronizadaPelaPluggy() {
        AppUser user = user();
        UUID accountId = UUID.randomUUID();
        Account account = Account.builder().id(accountId).owner(user).name("Nubank").bankName("Nubank")
                .type(AccountType.CORRENTE).active(true).syncedFromOpenFinance(true).build();
        when(currentUserProvider.getCurrentUser()).thenReturn(user);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        BalanceSnapshotCreateRequest request = new BalanceSnapshotCreateRequest(LocalDate.now(), BigDecimal.TEN);

        assertThatThrownBy(() -> service().registerBalanceSnapshot(accountId, request))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void deveRecusarSaldoManualDeContaDeOutroUsuario() {
        AppUser dono = user();
        AppUser outroUsuario = user();
        UUID accountId = UUID.randomUUID();
        Account account = Account.builder().id(accountId).owner(dono).name("Nubank").bankName("Nubank")
                .type(AccountType.CORRENTE).active(true).syncedFromOpenFinance(false).build();
        when(currentUserProvider.getCurrentUser()).thenReturn(outroUsuario);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        BalanceSnapshotCreateRequest request = new BalanceSnapshotCreateRequest(LocalDate.now(), BigDecimal.TEN);

        assertThatThrownBy(() -> service().registerBalanceSnapshot(accountId, request))
                .isInstanceOf(BusinessException.class);
    }
}
