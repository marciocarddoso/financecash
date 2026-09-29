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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class AccountSyncService {

    private static final Logger log = LoggerFactory.getLogger(AccountSyncService.class);

    /**
     * Quantos dias pra trás toda sincronização busca transações, sempre — não é uma janela que
     * encolhe baseada em {@code lastSyncAt}.
     *
     * <p><strong>Achado em produção (29/09)</strong>: a primeira versão calculava a janela a
     * partir do {@code lastSyncAt} anterior da conexão (com poucos dias de sobreposição), pra
     * evitar reconsultar toda vez o histórico inteiro. Só que {@code lastSyncAt} já vinha sendo
     * preenchido desde antes do TransactionImportService existir (sincronização de contas/
     * cartões, dias antes) — então na prática a janela nunca chegou a olhar 90 dias pra trás,
     * ficou sempre estreita (poucos dias), e nenhuma compra de cartão de fora dessa janela foi
     * importada. Como a deduplicação por (descrição, dueDate, valor) já torna reprocessar o
     * mesmo período seguro, a correção é simplesmente não encolher a janela: toda sincronização
     * busca sempre os últimos {@code transactionLookbackDays}.
     *
     * <p>Era 60 dias inicialmente (um ciclo de fatura inteiro com folga), depois 120 (a pedido
     * do Marcio, pra trazer compras/parcelamentos mais antigos na primeira importação de cada
     * conexão). Virou 365 (12 meses, 1/10, de novo a pedido do Marcio): com 120 dias não dava
     * pra ver o histórico completo de itens anuais (ex. tarifa de anuidade cobrada uma vez por
     * ano) nem confirmar parcelamentos de compras de cartão mais antigas contra a planilha.
     * Configurável via {@code financecash.pluggy.sync-window-days} (application.yml) em vez de
     * constante fixa, pra não precisar recompilar pra ajustar — primeiro passo pra, no futuro,
     * virar uma preferência por usuário (como já existe pra notificações, ver
     * {@code AppUser.notifySmsEnabled}/{@code /api/me/notification-preferences}) em vez de uma
     * única configuração global do servidor. Sem custo real de aumentar: é o mesmo número de
     * chamadas à Pluggy, só um intervalo de data maior, e a dedup protege de duplicar.
     */
    private final int transactionLookbackDays;

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
            TransactionImportService transactionImportService,
            @Value("${financecash.pluggy.sync-window-days:365}") int transactionLookbackDays) {
        this.transactionLookbackDays = transactionLookbackDays;
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

        LocalDate transactionsTo = LocalDate.now();
        LocalDate transactionsFrom = transactionsTo.minusDays(transactionLookbackDays);

        List<PluggyClient.AccountInfo> pluggyAccounts = pluggyClient.listAccounts(connection.getItemId());

        String connectionBankName = pluggyAccounts.stream()
                .filter(a -> "BANK".equals(a.type()))
                .map(this::resolveBankName)
                .filter(name -> name != null && !name.isBlank())
                .findFirst()
                .orElse(null);

        int accountsCreated = 0;
        int accountsUpdated = 0;
        int accountsSkipped = 0;
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

            if (!"BANK".equals(pluggyAccount.type())) {
                // Achado real (28/09, relatado pelo Marcio: rendimento do 99Pay não aparecia
                // depois de sincronizar): o sync só processa contas do tipo "BANK" (conta
                // corrente/poupança) e "CREDIT" (cartão) — qualquer outro tipo que a Pluggy
                // mandar (ex.: possivelmente "INVESTMENT" pra saldo com rendimento automático,
                // como carteiras tipo 99Pay/PicPay costumam expor) é ignorado silenciosamente
                // aqui, mesmo que `AccountType.INVESTIMENTO` já exista no domínio (hoje só usado
                // pra conta cadastrada manualmente). Log deliberado, mesma cautela de sempre com
                // a Pluggy: em vez de adivinhar o texto exato de `type`/`subtype` que ela manda
                // pra esse tipo de conta, log pra confirmar com dado real antes de mapear.
                log.warn("Conta ignorada na sincronização (tipo Pluggy não suportado ainda): "
                                + "itemId={}, accountId={}, type={}, subtype={}, name={}, marketingName={}, balance={}",
                        connection.getItemId(), pluggyAccount.id(), pluggyAccount.type(), pluggyAccount.subtype(),
                        pluggyAccount.name(), pluggyAccount.marketingName(), pluggyAccount.balance());
                continue;
            }

            AccountType type = mapType(pluggyAccount.subtype());
            if (type == null) {
                log.warn("Conta BANK ignorada na sincronização (subtype Pluggy não mapeado): "
                                + "itemId={}, accountId={}, subtype={}, name={}, marketingName={}, balance={}",
                        connection.getItemId(), pluggyAccount.id(), pluggyAccount.subtype(),
                        pluggyAccount.name(), pluggyAccount.marketingName(), pluggyAccount.balance());
                continue;
            }

            String bankName = resolveBankName(pluggyAccount);
            Account account = accountRepository.findByOwnerIdAndBankNameIgnoreCaseAndType(user.getId(), bankName, type)
                    .orElse(null);

            // Diagnóstico (28/09, oitava rodada — Marcio relatou que o saldo do 99Pay continua
            // desatualizado mesmo depois do fix de conta desativada acima, e que precisou apagar
            // contas "duplicadas" direto no banco pra limpar a tela). Hipótese ainda NÃO
            // confirmada, só levantada por leitura de código: `bankName` (chave de busca pra não
            // duplicar, ver resolveBankName) vem de `marketingName`/`name` da própria Pluggy — se
            // esse texto vier ligeiramente diferente entre uma sincronização e outra (espaço a
            // mais/a menos, por exemplo, que `IgnoreCase` não cobre), a busca não acha a conta já
            // cadastrada e cria uma linha nova a cada sync — explicaria tanto as duplicatas que
            // ele apagou quanto o saldo "desatualizado" (na real teria uma linha nova com o saldo
            // certo, só que ele estava olhando/editando a linha antiga). bankName entre aspas de
            // propósito pra expor espaço invisível no log. Confirma ou descarta com dado real na
            // próxima sincronização, em vez de adivinhar — mesma cautela de sempre com a Pluggy.
            log.info("Sincronizando conta BANK: itemId={}, pluggyAccountId={}, bankName=\"{}\", "
                            + "type={}, contaExistente={}, saldoRecebidoDaPluggy={}",
                    connection.getItemId(), pluggyAccount.id(), bankName, type,
                    account != null ? account.getId() : "NOVA (nenhuma conta encontrada pra esse bankName+type)",
                    pluggyAccount.balance());

            if (account != null && !account.isActive()) {
                // Achado real (28/09, relatado pelo Marcio: ele desativou a conta 99Pay e
                // sincronizou de novo esperando que ela reaparecesse em Contas & Saldos, mas
                // continuou sumida, com o saldo antigo ainda visível em Lançamentos) — essa
                // busca (findByOwnerIdAndBankNameIgnoreCaseAndType) não filtra por active,
                // então achava a conta desativada e ficava atualizando o snapshot/importando
                // transações dela pra sempre, escondida (listActive() filtra active=true), como
                // uma conta "fantasma". Decisão (comportamento profissional): uma vez
                // desativada, a conta nunca mais é tocada pelo sync — se o usuário quiser voltar
                // a sincronizar esse banco, precisa reativar a conta manualmente.
                log.warn("Conta ignorada na sincronização (desativada pelo usuário): "
                                + "itemId={}, accountId={}, bankName={}, type={}",
                        connection.getItemId(), account.getId(), bankName, type);
                accountsSkipped++;
                continue;
            }

            if (account == null) {
                account = Account.builder()
                        .owner(user)
                        .name(friendlyAccountName(bankName, type))
                        .bankName(bankName)
                        .type(type)
                        .active(true)
                        .syncedFromOpenFinance(true)
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

        return new SyncResultDTO(accountsCreated, accountsUpdated, accountsSkipped, creditCardsCreated,
                creditCardsUpdated, creditCardsSkipped, creditCardsWithEstimatedClosingDay, entriesImported);
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
                    .brand(creditData.brand())
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
        // Cartão sincronizado antes da migration V7 (brand) fica com brand null até o próximo
        // sync passar por aqui — refaz sempre que a Pluggy mandar um valor, pra não ficar preso
        // num brand desatualizado se o banco corrigir o dado no futuro (01/10, quinta rodada).
        if (creditData.brand() != null && !creditData.brand().isBlank()) {
            card.setBrand(creditData.brand());
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
