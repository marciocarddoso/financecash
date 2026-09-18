package br.com.financecash.application.service;

import br.com.financecash.application.dto.AccountCreateRequest;
import br.com.financecash.application.dto.AccountDTO;
import br.com.financecash.application.dto.BalanceSnapshotCreateRequest;
import br.com.financecash.domain.model.Account;
import br.com.financecash.domain.model.AppUser;
import br.com.financecash.domain.model.BalanceSnapshot;
import br.com.financecash.domain.repository.AccountRepository;
import br.com.financecash.domain.repository.BalanceSnapshotRepository;
import br.com.financecash.exception.ResourceNotFoundException;
import br.com.financecash.security.CurrentUserProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final BalanceSnapshotRepository balanceSnapshotRepository;
    private final CurrentUserProvider currentUserProvider;

    public AccountService(AccountRepository accountRepository, BalanceSnapshotRepository balanceSnapshotRepository,
                           CurrentUserProvider currentUserProvider) {
        this.accountRepository = accountRepository;
        this.balanceSnapshotRepository = balanceSnapshotRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional(readOnly = true)
    public List<AccountDTO> listActive() {
        AppUser user = currentUserProvider.getCurrentUser();
        return accountRepository.findByOwnerIdAndActiveTrueOrderByNameAsc(user.getId()).stream()
                .map(account -> {
                    var snapshot = balanceSnapshotRepository
                            .findFirstByAccountIdOrderByReferenceDateDescCreatedAtDesc(account.getId())
                            .orElse(null);
                    return AccountDTO.from(account,
                            snapshot != null ? snapshot.getBalance() : null,
                            snapshot != null ? snapshot.getReferenceDate() : null);
                })
                .toList();
    }

    @Transactional
    public AccountDTO create(AccountCreateRequest request) {
        AppUser user = currentUserProvider.getCurrentUser();
        Account account = Account.builder()
                .owner(user)
                .name(request.name())
                .bankName(request.bankName())
                .type(request.type())
                .investmentDescription(request.investmentDescription())
                .active(true)
                .build();
        Account saved = accountRepository.save(account);
        return AccountDTO.from(saved, null, null);
    }

    @Transactional
    public void registerBalanceSnapshot(UUID accountId, BalanceSnapshotCreateRequest request) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Conta não encontrada: " + accountId));

        BalanceSnapshot snapshot = BalanceSnapshot.builder()
                .account(account)
                .referenceDate(request.referenceDate())
                .balance(request.balance())
                .build();
        balanceSnapshotRepository.save(snapshot);
    }

    /** Soma o saldo mais recente de cada conta ativa do usuário — usado pelo DashboardService. */
    @Transactional(readOnly = true)
    public BigDecimal consolidatedBalance(UUID ownerId) {
        return balanceSnapshotRepository.findLatestSnapshotPerAccount(ownerId).stream()
                .map(BalanceSnapshot::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
