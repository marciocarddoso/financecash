package br.com.financecash.application.service;

import br.com.financecash.application.dto.AccountCreateRequest;
import br.com.financecash.application.dto.AccountDTO;
import br.com.financecash.application.dto.AccountUpdateRequest;
import br.com.financecash.application.dto.BalanceSnapshotCreateRequest;
import br.com.financecash.domain.model.Account;
import br.com.financecash.domain.model.AppUser;
import br.com.financecash.domain.model.BalanceSnapshot;
import br.com.financecash.domain.repository.AccountRepository;
import br.com.financecash.domain.repository.BalanceSnapshotRepository;
import br.com.financecash.exception.BusinessException;
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
        return toDTOs(accountRepository.findByOwnerIdAndActiveTrueOrderByNameAsc(user.getId()));
    }

    /**
     * Lista todas as contas do usuário, ativas e inativas — usada pela tela Contas & Saldos,
     * que agora precisa mostrar contas desativadas pra permitir reativá-las (o "ativo/inativo"
     * é o único controle da tela pra conta sincronizada, ver activate()/deactivate()). O
     * Dashboard continua usando só consolidatedBalance(), que soma apenas contas ativas — essa
     * listagem completa não afeta o saldo consolidado.
     */
    @Transactional(readOnly = true)
    public List<AccountDTO> listAll() {
        AppUser user = currentUserProvider.getCurrentUser();
        return toDTOs(accountRepository.findByOwnerIdOrderByNameAsc(user.getId()));
    }

    private List<AccountDTO> toDTOs(List<Account> accounts) {
        return accounts.stream()
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
                .syncedFromOpenFinance(false)
                .build();
        Account saved = accountRepository.save(account);
        return AccountDTO.from(saved, null, null);
    }

    /**
     * Corrige nome/banco/tipo de uma conta criada manualmente (ex.: erro de digitação no
     * cadastro). Contas sincronizadas pela Pluggy não podem ser editadas por aqui — nome/banco/
     * tipo/saldo delas vêm do banco via AccountSyncService; o único controle que o usuário tem
     * sobre elas é ativar/desativar (28/09, sétima rodada, a pedido do Marcio: "pra conta
     * sincronizada pela Pluggy, ela deveria ser praticamente só leitura ... a edição completa
     * só faz sentido de verdade pra conta manual").
     */
    @Transactional
    public AccountDTO update(UUID accountId, AccountUpdateRequest request) {
        AppUser user = currentUserProvider.getCurrentUser();
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Conta não encontrada: " + accountId));
        if (!account.getOwner().getId().equals(user.getId())) {
            throw new BusinessException("Esta conta não pertence ao usuário logado.");
        }
        if (account.isSyncedFromOpenFinance()) {
            throw new BusinessException(
                    "Esta conta é sincronizada automaticamente e não pode ser editada manualmente.");
        }

        account.setName(request.name());
        account.setBankName(request.bankName());
        account.setType(request.type());
        account.setInvestmentDescription(request.investmentDescription());
        Account saved = accountRepository.save(account);

        var snapshot = balanceSnapshotRepository
                .findFirstByAccountIdOrderByReferenceDateDescCreatedAtDesc(saved.getId())
                .orElse(null);
        return AccountDTO.from(saved, snapshot != null ? snapshot.getBalance() : null,
                snapshot != null ? snapshot.getReferenceDate() : null);
    }

    /**
     * Registra um saldo manual — só permitido em conta manual. Conta sincronizada tem o saldo
     * atualizado pelo AccountSyncService a cada sincronização; deixar o usuário sobrescrever
     * manualmente criaria uma divergência que o próximo sync ia apagar sem avisar (28/09,
     * sétima rodada). De quebra, corrige uma falha de autorização que essa checagem não tinha:
     * antes não validava se a conta pertencia ao usuário logado.
     */
    @Transactional
    public void registerBalanceSnapshot(UUID accountId, BalanceSnapshotCreateRequest request) {
        AppUser user = currentUserProvider.getCurrentUser();
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Conta não encontrada: " + accountId));
        if (!account.getOwner().getId().equals(user.getId())) {
            throw new BusinessException("Esta conta não pertence ao usuário logado.");
        }
        if (account.isSyncedFromOpenFinance()) {
            throw new BusinessException(
                    "Esta conta é sincronizada automaticamente e não aceita saldo manual.");
        }

        BalanceSnapshot snapshot = BalanceSnapshot.builder()
                .account(account)
                .referenceDate(request.referenceDate())
                .balance(request.balance())
                .build();
        balanceSnapshotRepository.save(snapshot);
    }

    /**
     * Desativa (soft delete) uma conta — usado quando o usuário substitui uma conta
     * criada manualmente por uma equivalente trazida pelo AccountSyncService, ou
     * simplesmente não usa mais aquela conta. Não apaga histórico (BalanceSnapshot
     * continua existindo), só some da listagem (listActive já filtra por active=true).
     */
    @Transactional
    public void deactivate(UUID accountId) {
        AppUser user = currentUserProvider.getCurrentUser();
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Conta não encontrada: " + accountId));
        if (!account.getOwner().getId().equals(user.getId())) {
            throw new BusinessException("Esta conta não pertence ao usuário logado.");
        }
        account.setActive(false);
        accountRepository.save(account);
    }

    /**
     * Reativa uma conta desativada. Pra conta sincronizada, é o único jeito de fazer o
     * AccountSyncService voltar a tocar nela (ver o skip em AccountSyncService.syncConnection) —
     * o usuário decide explicitamente, o sync nunca reativa sozinho.
     */
    @Transactional
    public void activate(UUID accountId) {
        AppUser user = currentUserProvider.getCurrentUser();
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Conta não encontrada: " + accountId));
        if (!account.getOwner().getId().equals(user.getId())) {
            throw new BusinessException("Esta conta não pertence ao usuário logado.");
        }
        account.setActive(true);
        accountRepository.save(account);
    }

    /** Soma o saldo mais recente de cada conta ativa do usuário — usado pelo DashboardService. */
    @Transactional(readOnly = true)
    public BigDecimal consolidatedBalance(UUID ownerId) {
        return balanceSnapshotRepository.findLatestSnapshotPerAccount(ownerId).stream()
                .map(BalanceSnapshot::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
