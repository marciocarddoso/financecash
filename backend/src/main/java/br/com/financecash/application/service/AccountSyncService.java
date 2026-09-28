package br.com.financecash.application.service;

import br.com.financecash.application.dto.SyncResultDTO;
import br.com.financecash.domain.model.Account;
import br.com.financecash.domain.model.AccountType;
import br.com.financecash.domain.model.AppUser;
import br.com.financecash.domain.model.BalanceSnapshot;
import br.com.financecash.domain.model.BankConnection;
import br.com.financecash.domain.model.CreditCard;
import br.com.financecash.domain.repository.AccountRepository;
import br.com.financecash.domain.repository.BalanceSnapshotRepository;
import br.com.financecash.domain.repository.BankConnectionRepository;
import br.com.financecash.domain.repository.CreditCardRepository;
import br.com.financecash.exception.ResourceNotFoundException;
import br.com.financecash.openfinance.PluggyClient;
import br.com.financecash.security.CurrentUserProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
public class AccountSyncService {

    /** Quantos dias antes do último sync a janela de transações começa, para cobrir POSTED que só assentaram depois. */
    private static final int SYNC_OVERLAP_DAYS = 3;
    /** Janela de transações na primeira sincronização de uma conexão (sem lastSyncAt ainda). */
    private static final int FIRST_SYNC_LOOKBACK_DAYS = 90;

    private final AccountRepository accountRepository;
    private final BalanceSnapshotRepository balanceSnapshotRepository;
    private final BankConnectionRepository bankConnectionRepository;
    private final CreditCardRepository creditCardRepository;
    private final PluggyClient pluggyClient;
    private final CurrentUserProvider currentUserProvider;
    private final TransactionImportService transactionImportService;

    public AccountSyncService(
            AccountRepository accountRepository,
            BalanceSnapshotRepository balanceSnapshotRepository,
            BankConnectionRepository bankConnectionRepository,
            CreditCardRepository creditCardRepository,
            PluggyClient pluggyClient,
            CurrentUserProvider currentUserProvider,
            TransactionImportService transactionImportService) {
        this.accountRepository = accountRepository;
        this.balanceSnapshotRepository = balanceSnapshotRepository;
        this.bankConnectionRepository = bankConnectionRepository;
        this.creditCardRepository = creditCardRepository;
        this.pluggyClient = pluggyClient;
        this.currentUserProvider = currentUserProvider;
        this.transactionImportService = transactionImportService;
    }

    @Transactional
    public SyncResultDTO syncConnection(UUID connectionId) {
        AppUser user = currentUserProvider.getCurrentUser();
        BankConnection connection = bankConnectionRepository.findById(connectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Conexão não encontrada: " + connectionId));
        if (!connection.getOwner().getId().equals(user.getId())) {
            throw new ResourceNotFoundException("Conexão não encontrada: " + connectionId);
        }

        Instant previousLastSyncAt = connection.getLastSyncAt();
        LocalDate transactionsTo = LocalDate.now();
        LocalDate transactionsFrom = previousLastSyncAt != null
                ? previousLastSyncAt.atZone(ZoneId.systemDefault()).toLocalDate().minusDays(SYNC_OVERLAP_DAYS)
                : transactionsTo.minusDays(FIRST_SYNC_LOOKBACK_DAYS);

        List<PluggyClient.AccountInfo> pluggyAccounts = pluggyClient.listAccounts(connection.getItemId());

        String connectionBankName = pluggyAccounts.stream()
                .filter(a -> "BANK".equals(a.type()))
                .map(this::resolveBankName)
                .filter(name -> name != null && !name.isBlank())
                .findFirst()
                .orElse(null);

        int accountsCreated = 0;
        int accountsUpdated = 0;
        int creditCardsCreated = 0;
        int creditCardsUpdated = 0;
        int creditCardsSkipped = 0;
        int creditCardsWithEstimatedClosingDay = 0;
        int entriesImported = 0;

        for (PluggyClient.AccountInfo pluggyAccount : pluggyAccounts) {
            if ("CREDIT".equals(pluggyAccount.type())) {
                CreditCardSyncResult cardResult = syncCreditCard(user, pluggyAccount, connectionBankName);
                switch (cardResult.outcome()) {
                    case CREATED -> {
                        creditCardsCreated++;
                        if (cardResult.closingDayEstimated()) {
                            creditCardsWithEstimatedClosingDay++;
                        }
                    }
                    case UPDATED -> creditCardsUpdated++;
                    case SKIPPED -> creditCardsSkipped++;
                }
                if (cardResult.card() != null) {
                    entriesImported += transactionImportService.importForCreditCard(
                            user, cardResult.card(), pluggyAccount.id(), transactionsFrom, transactionsTo);
                }
                continue;
            }

            AccountType type = mapType(pluggyAccount.subtype());
            if (!"BANK".equals(pluggyAccount.type()) || type == null) {
                continue;
            }

            String bankName = resolveBankName(pluggyAccount);
            Account account = accountRepository.findByOwnerIdAndBankNameIgnoreCaseAndType(user.getId(), bankName, type)
                    .orElse(null);

            if (account == null) {
                account = Account.builder()
                        .owner(user)
                        .name(friendlyAccountName(bankName, type))
                        .bankName(bankName)
                        .type(type)
                        .active(true)
                        .build();
                account = accountRepository.save(account);
                accountsCreated++;
            } else {
                accountsUpdated++;
            }

            upsertTodaySnapshot(account, pluggyAccount.balance());
            entriesImported += transactionImportService.importForAccount(
                    user, account, pluggyAccount.id(), transactionsFrom, transactionsTo);
        }

        connection.setLastSyncAt(Instant.now());
        bankConnectionRepository.save(connection);

        return new SyncResultDTO(accountsCreated, accountsUpdated, creditCardsCreated, creditCardsUpdated,
                creditCardsSkipped, creditCardsWithEstimatedClosingDay, entriesImported);
    }

    private CreditCardSyncResult syncCreditCard(AppUser user, PluggyClient.AccountInfo pluggyAccount, String connectionBankName) {
        PluggyClient.CreditDataInfo creditData = pluggyAccount.creditData();
        if (connectionBankName == null || creditData == null || creditData.balanceDueDate() == null) {
            return new CreditCardSyncResult(CreditCardSyncOutcome.SKIPPED, false, null);
        }

        int dueDay = creditData.balanceDueDate().getDayOfMonth();
        boolean closingDayKnown = creditData.balanceCloseDate() != null;

        CreditCard card = creditCardRepository.findByOwnerIdAndBankNameIgnoreCase(user.getId(), connectionBankName)
                .orElse(null);

        if (card == null) {
            int closingDay = closingDayKnown
                    ? creditData.balanceCloseDate().getDayOfMonth()
                    : estimateClosingDay(creditData.balanceDueDate());
            card = CreditCard.builder()
                    .owner(user)
                    .name(friendlyCardName(connectionBankName, creditData.brand()))
                    .bankName(connectionBankName)
                    .closingDay(closingDay)
                    .dueDay(dueDay)
                    .active(true)
                    .build();
            card = creditCardRepository.save(card);
            return new CreditCardSyncResult(CreditCardSyncOutcome.CREATED, !closingDayKnown, card);
        }

        card.setDueDay(dueDay);
        if (closingDayKnown) {
            card.setClosingDay(creditData.balanceCloseDate().getDayOfMonth());
        }
        card = creditCardRepository.save(card);
        return new CreditCardSyncResult(CreditCardSyncOutcome.UPDATED, false, card);
    }

    private int estimateClosingDay(LocalDate balanceDueDate) {
        return balanceDueDate.minusDays(10).getDayOfMonth();
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

    private String resolveBankName(PluggyClient.AccountInfo pluggyAccount) {
        return pluggyAccount.marketingName() != null && !pluggyAccount.marketingName().isBlank()
                ? pluggyAccount.marketingName()
                : pluggyAccount.name();
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

    private String friendlyAccountName(String bankName, AccountType type) {
        String suffix = type == AccountType.POUPANCA ? "Poupança" : "Conta Corrente";
        return bankName + " - " + suffix;
    }

    private String friendlyCardName(String bankName, String brand) {
        return (brand != null && !brand.isBlank())
                ? bankName + " - Cartão (" + brand + ")"
                : bankName + " - Cartão";
    }

    private enum CreditCardSyncOutcome {
        CREATED, UPDATED, SKIPPED
    }

    private record CreditCardSyncResult(CreditCardSyncOutcome outcome, boolean closingDayEstimated, CreditCard card) {
    }
}
