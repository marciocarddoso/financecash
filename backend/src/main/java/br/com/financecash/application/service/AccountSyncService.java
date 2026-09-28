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
import java.util.List;
import java.util.UUID;

/**
 * Sincroniza o que a Pluggy traz de uma conexão para o modelo de domínio do FinanceCash
 * (Account/BalanceSnapshot para contas tipo BANK, CreditCard para contas tipo CREDIT) — ver
 * docs/OPEN-FINANCE-E-BOLETOS.md, seção 2.3.
 *
 * Regra combinada com o usuário: não duplicar contas — casa pelo par (nome do banco, tipo
 * de conta) já cadastrado e só substitui o saldo do dia; se não existir, cria a conta.
 * Cartões seguem a mesma ideia, casando só por nome do banco (CreditCard não tem campo de
 * tipo) — ver syncCreditCard.
 */
@Service
public class AccountSyncService {

    private final AccountRepository accountRepository;
    private final BalanceSnapshotRepository balanceSnapshotRepository;
    private final BankConnectionRepository bankConnectionRepository;
    private final CreditCardRepository creditCardRepository;
    private final PluggyClient pluggyClient;
    private final CurrentUserProvider currentUserProvider;

    public AccountSyncService(
            AccountRepository accountRepository,
            BalanceSnapshotRepository balanceSnapshotRepository,
            BankConnectionRepository bankConnectionRepository,
            CreditCardRepository creditCardRepository,
            PluggyClient pluggyClient,
            CurrentUserProvider currentUserProvider) {
        this.accountRepository = accountRepository;
        this.balanceSnapshotRepository = balanceSnapshotRepository;
        this.bankConnectionRepository = bankConnectionRepository;
        this.creditCardRepository = creditCardRepository;
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

        List<PluggyClient.AccountInfo> pluggyAccounts = pluggyClient.listAccounts(connection.getItemId());

        // Contas CREDIT da Pluggy não trazem um nome de banco confiável (ex.: "OUTROS",
        // "BANDEIRADO" em vez de "Banco Bradesco"). Como cada BankConnection/item representa
        // um único banco real, usamos o nome descoberto numa conta BANK da mesma conexão.
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
                continue;
            }

            AccountType type = mapType(pluggyAccount.subtype());
            if (!"BANK".equals(pluggyAccount.type()) || type == null) {
                // Tipo/subtipo que a Pluggy ainda não documentou pra gente (ex.: INVESTMENT).
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
        }

        connection.setLastSyncAt(Instant.now());
        bankConnectionRepository.save(connection);

        return new SyncResultDTO(accountsCreated, accountsUpdated, creditCardsCreated, creditCardsUpdated,
                creditCardsSkipped, creditCardsWithEstimatedClosingDay);
    }

    /**
     * Sincroniza um cartão de crédito. Precisa do nome do banco (vem de uma conta BANK da
     * mesma conexão, não do próprio cartão) e de creditData.balanceDueDate. Sem qualquer um
     * dos dois, não dá pra sincronizar com segurança — fica como "skipped".
     *
     * creditData.balanceCloseDate (dia de fechamento) veio nulo nos 3 cartões reais testados
     * (Bradesco, C6, Nubank via MeuPluggy) mesmo com balanceDueDate preenchido — não é um bug
     * de parsing, a Pluggy simplesmente não manda esse campo nesse conector/tier. Pra não
     * travar a sincronização por causa disso: ao ATUALIZAR um cartão já cadastrado, só mexe
     * em dueDay (o closingDay que já está lá, seja do usuário ou de uma estimativa anterior,
     * fica intacto); ao CRIAR um cartão novo, estima o fechamento como 10 dias antes do
     * vencimento (convenção comum, mas é só uma estimativa) e marca closingDayEstimated=true,
     * pra a tela avisar o usuário a conferir/corrigir via "Editar" em Cartões.
     */
    private CreditCardSyncResult syncCreditCard(AppUser user, PluggyClient.AccountInfo pluggyAccount, String connectionBankName) {
        PluggyClient.CreditDataInfo creditData = pluggyAccount.creditData();
        if (connectionBankName == null || creditData == null || creditData.balanceDueDate() == null) {
            return new CreditCardSyncResult(CreditCardSyncOutcome.SKIPPED, false);
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
            creditCardRepository.save(card);
            return new CreditCardSyncResult(CreditCardSyncOutcome.CREATED, !closingDayKnown);
        }

        card.setDueDay(dueDay);
        if (closingDayKnown) {
            card.setClosingDay(creditData.balanceCloseDate().getDayOfMonth());
        }
        creditCardRepository.save(card);
        return new CreditCardSyncResult(CreditCardSyncOutcome.UPDATED, false);
    }

    /** Estimativa (não confirmada pela Pluggy): fatura fecha ~10 dias antes de vencer. */
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

    private record CreditCardSyncResult(CreditCardSyncOutcome outcome, boolean closingDayEstimated) {
    }
}
