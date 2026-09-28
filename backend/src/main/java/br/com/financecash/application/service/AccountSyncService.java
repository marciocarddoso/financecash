package br.com.financecash.application.service;

import br.com.financecash.application.dto.SyncResultDTO;
import br.com.financecash.domain.model.Account;
import br.com.financecash.domain.model.AccountType;
import br.com.financecash.domain.model.AppUser;
import br.com.financecash.domain.model.BalanceSnapshot;
import br.com.financecash.domain.model.BankConnection;
import br.com.financecash.domain.repository.AccountRepository;
import br.com.financecash.domain.repository.BalanceSnapshotRepository;
import br.com.financecash.domain.repository.BankConnectionRepository;
import br.com.financecash.exception.ResourceNotFoundException;
import br.com.financecash.openfinance.PluggyClient;
import br.com.financecash.security.CurrentUserProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Sincroniza as contas bancárias (tipo BANK) que a Pluggy traz de uma conexão para o
 * modelo de domínio do FinanceCash (Account/BalanceSnapshot) — ver
 * docs/OPEN-FINANCE-E-BOLETOS.md, seção 2.3.
 *
 * Regra combinada com o usuário: não duplicar contas — casa pelo par (nome do banco,
 * tipo de conta) já cadastrado e só substitui o saldo do dia; se não existir, cria a
 * conta. Cartões de crédito (tipo CREDIT na Pluggy) ainda não são sincronizados aqui,
 * porque exigem dados de fatura (dia de fechamento/vencimento) que o PluggyClient ainda
 * não extrai — ficam contados em creditCardsSkipped pra transparência na tela.
 */
@Service
public class AccountSyncService {

    private final AccountRepository accountRepository;
    private final BalanceSnapshotRepository balanceSnapshotRepository;
    private final BankConnectionRepository bankConnectionRepository;
    private final PluggyClient pluggyClient;
    private final CurrentUserProvider currentUserProvider;

    public AccountSyncService(
            AccountRepository accountRepository,
            BalanceSnapshotRepository balanceSnapshotRepository,
            BankConnectionRepository bankConnectionRepository,
            PluggyClient pluggyClient,
            CurrentUserProvider currentUserProvider) {
        this.accountRepository = accountRepository;
        this.balanceSnapshotRepository = balanceSnapshotRepository;
        this.bankConnectionRepository = bankConnectionRepository;
        this.pluggyClient = pluggyClient;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional
    public SyncResultDTO syncConnection(UUID connectionId) {
        AppUser user = currentUserProvider.getCurrentUser();
        BankConnection connection = bankConnectionRepository.findById(connectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Conexão não encontrada: " + connectionId));
        if (!connection.getOwner().getId().equals(user.getId())) {
            throw new ResourceNotFoundException("Conexão não encontrada: " + connectionId);
        }

        int created = 0;
        int updated = 0;
        int creditCardsSkipped = 0;

        for (PluggyClient.AccountInfo pluggyAccount : pluggyClient.listAccounts(connection.getItemId())) {
            if ("CREDIT".equals(pluggyAccount.type())) {
                creditCardsSkipped++;
                continue;
            }

            AccountType type = mapType(pluggyAccount.subtype());
            if (!"BANK".equals(pluggyAccount.type()) || type == null) {
                // Tipo/subtipo que a Pluggy ainda não documentou pra gente (ex.: INVESTMENT) —
                // não é cartão de crédito, mas também não sabemos mapear com segurança ainda.
                continue;
            }

            String bankName = pluggyAccount.marketingName() != null && !pluggyAccount.marketingName().isBlank()
                    ? pluggyAccount.marketingName()
                    : pluggyAccount.name();

            Account account = accountRepository.findByOwnerIdAndBankNameIgnoreCaseAndType(user.getId(), bankName, type)
                    .orElse(null);

            if (account == null) {
                account = Account.builder()
                        .owner(user)
                        .name(friendlyName(bankName, type))
                        .bankName(bankName)
                        .type(type)
                        .active(true)
                        .build();
                account = accountRepository.save(account);
                created++;
            } else {
                updated++;
            }

            upsertTodaySnapshot(account, pluggyAccount.balance());
        }

        connection.setLastSyncAt(Instant.now());
        bankConnectionRepository.save(connection);

        return new SyncResultDTO(created, updated, creditCardsSkipped);
    }

    private void upsertTodaySnapshot(Account account, BigDecimal balance) {
        if (balance == null) {
            return;
        }
        LocalDate today = LocalDate.now();
        BalanceSnapshot snapshot = balanceSnapshotRepository.findByAccountIdAndReferenceDate(account.getId(), today)
                .orElseGet(() -> BalanceSnapshot.builder().account(account).referenceDate(today).build());
        snapshot.setBalance(balance);
        balanceSnapshotRepository.save(snapshot);
    }

    private AccountType mapType(String pluggySubtype) {
        if (pluggySubtype == null) {
            return null;
        }
        return switch (pluggySubtype) {
            case "CHECKING_ACCOUNT" -> AccountType.CORRENTE;
            case "SAVINGS_ACCOUNT" -> AccountType.POUPANCA;
            default -> null;
        };
    }

    private String friendlyName(String bankName, AccountType type) {
        String suffix = type == AccountType.POUPANCA ? "Poupança" : "Conta Corrente";
        return bankName + " - " + suffix;
    }
}
