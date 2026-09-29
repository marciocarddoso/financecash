package br.com.financecash.application.service;

import br.com.financecash.domain.model.Account;
import br.com.financecash.domain.model.AppUser;
import br.com.financecash.domain.model.Category;
import br.com.financecash.domain.model.CategoryType;
import br.com.financecash.domain.model.CreditCard;
import br.com.financecash.domain.model.Entry;
import br.com.financecash.domain.model.EntryOrigin;
import br.com.financecash.domain.model.EntryStatus;
import br.com.financecash.domain.model.EntryType;
import br.com.financecash.domain.model.InvoiceSettlement;
import br.com.financecash.domain.repository.CategoryRepository;
import br.com.financecash.domain.repository.EntryRepository;
import br.com.financecash.domain.repository.InvoiceSettlementRepository;
import br.com.financecash.openfinance.PluggyCategoryTranslator;
import br.com.financecash.openfinance.PluggyClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Importa as transações trazidas ao vivo pela Pluggy (PluggyClient.listTransactions) como
 * Entry — chamado pelo AccountSyncService uma vez para cada Account/CreditCard sincronizado.
 * Não existe endpoint próprio: é sempre parte do fluxo de sincronização de uma BankConnection.
 *
 * <p>Reaproveita os mesmos padrões do EntryImportService (importação via CSV): dedup por
 * (description, dueDate, amount) e categoria resolvida/criada por nome. A diferença principal
 * entre conta bancária e cartão de crédito é a convenção de sinal da Pluggy: numa conta,
 * amount positivo é entrada (RECEITA) e negativo é saída (DESPESA); num cartão, amount
 * positivo é uma compra (débito na fatura) e negativo é pagamento/estorno da fatura — esses
 * pagamentos são ignorados aqui de propósito, porque já aparecem como um débito na conta
 * bancária que paga a fatura, e importar os dois lados duplicaria o gasto.</p>
 */
@Service
public class TransactionImportService {

    private static final Logger log = LoggerFactory.getLogger(TransactionImportService.class);
    private static final String FALLBACK_CATEGORY_NAME = "Outros";

    private final PluggyClient pluggyClient;
    private final EntryRepository entryRepository;
    private final CategoryRepository categoryRepository;
    private final InvoiceSettlementRepository invoiceSettlementRepository;

    public TransactionImportService(PluggyClient pluggyClient, EntryRepository entryRepository,
                                     CategoryRepository categoryRepository,
                                     InvoiceSettlementRepository invoiceSettlementRepository) {
        this.pluggyClient = pluggyClient;
        this.entryRepository = entryRepository;
        this.categoryRepository = categoryRepository;
        this.invoiceSettlementRepository = invoiceSettlementRepository;
    }

    /**
     * Importa as transações de uma conta corrente/poupança já resolvida como Account. Cada
     * transação POSTED vira um Entry já PAGO (é um movimento que já aconteceu na conta).
     *
     * <p><strong>Achado em produção (01/10, terceira rodada, a pedido do Marcio: "o total de
     * despesa e o de receita estão fora da realidade")</strong>: comparando a soma de setembro
     * por categoria, dois tipos de lançamento do extrato inflavam o totalizador (ver
     * EntryRepository.sumByFiltersAccrual) sem representar gasto/receita real: (a) transferência entre
     * as próprias contas do usuário (categoria "Same person transfer"/variações da Pluggy,
     * R$19.549,50 + R$16.049,88 num mês só) — dinheiro mudando de bolso, não gasto; (b) o débito
     * em conta que paga a fatura do cartão (mesma transação de liquidação já reconhecida em
     * importForCreditCard/isInvoiceSettlementTransaction, só que aqui do lado da conta que
     * pagou — R$6.513,88 no mesmo mês) — o gasto real já foi contado quando a compra foi feita
     * no cartão, contar de novo aqui duplica. Confirmado com o Marcio (AskUserQuestion) excluir
     * os dois do totalizador, sem escondê-los da lista de Lançamentos — ver
     * Entry.excludedFromTotals / isExcludedFromTotals.
     *
     * @return quantos lançamentos novos foram importados (duplicatas e transações não-POSTED
     *         são puladas silenciosamente, sem contar).
     */
    public int importForAccount(AppUser user, Account account, String pluggyAccountId, LocalDate from, LocalDate to) {
        List<PluggyClient.TransactionInfo> transactions = pluggyClient.listTransactions(pluggyAccountId, from, to);
        int imported = 0;
        for (PluggyClient.TransactionInfo tx : transactions) {
            if (!isUsableAccountTransaction(tx) || tx.amount().compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }

            EntryType type = tx.amount().compareTo(BigDecimal.ZERO) > 0 ? EntryType.RECEITA : EntryType.DESPESA;
            BigDecimal amount = tx.amount().abs();
            String description = resolveDescription(tx);

            if (entryRepository.existsByOwnerIdAndDescriptionAndDueDateAndAmount(user.getId(), description, tx.date(), amount)) {
                continue;
            }

            Category category = resolveCategory(user, tx.category(), type);
            boolean excludedFromTotals = isExcludedFromTotals(tx, description);

            Entry entry = Entry.builder()
                    .owner(user)
                    .description(description)
                    .amount(amount)
                    .dueDate(tx.date())
                    .paymentDate(tx.date())
                    .type(type)
                    .status(EntryStatus.PAGO)
                    .origin(EntryOrigin.IMPORTADO_EXTRATO)
                    .category(category)
                    .excludedFromTotals(excludedFromTotals)
                    .account(account)
                    .build();
            entryRepository.save(entry);
            imported++;
        }
        return imported;
    }

    /**
     * Importa as transações de um cartão de crédito já resolvido como CreditCard.
     *
     * <p>Compras (amount positivo, na convenção da Pluggy para cartões) viram Entry DESPESA
     * PENDENTE, com dueDate calculado a partir do fechamento/vencimento do cartão.
     *
     * <p><strong>Achado em produção (01/10, a pedido do Marcio, com print real do app do C6)</strong>:
     * até aqui, TODA transação negativa era ignorada, o que descartava silenciosamente
     * estornos/créditos pontuais (ex.: tarifa de anuidade isenta por 1 ano, cobrada e
     * estornada no mesmo dia). Corrigido pra importar transação negativa como Entry RECEITA
     * PENDENTE — só que o primeiro corte (ignorar só descrição contendo "pagamento de fatura")
     * era simplista demais pra produção real.
     *
     * <p><strong>Achado em produção (01/10, segunda rodada, comparando saldo líquido por
     * cartão/mês com o valor real de fatura do app do C6)</strong>: cada banco (e o C6 sozinho,
     * em mais de uma situação) usa um texto diferente pra registrar a liquidação da fatura —
     * nenhum deles usa literalmente "pagamento de fatura". Confirmado com dado real dos 3
     * bancos: Bradesco manda "PAGTO. POR DEB EM C/C"; C6 manda "Inclusao de Pagamento Ciclo
     * Corrente" (pagamento normal da fatura), "Pagamento Ent parcelamento fat" e "Credito de
     * Refinanciamento Saldo Financiado" (quando o saldo da fatura é financiado/parcelado pelo
     * banco — inclusive com o mesmo valor aparecendo de novo como "Pagamento recebido", parece
     * ser o mesmo evento bancário chegando em duas transações Pluggy distintas); Nu manda
     * "Pagamento recebido". Como cada um desses valores é grande (o tamanho da fatura inteira
     * ou de uma parcela dela), importar qualquer um como RECEITA zera boa parte das compras
     * reais do mês no saldo líquido — foi exatamente esse o bug que fez "os valores por cartão"
     * ficarem sem sentido depois do fix anterior.
     *
     * <p>Em vez de tentar listar todo texto de "isso é pagamento" (perde pra qualquer banco
     * novo ou variação futura — mesmo problema que categoria em inglês já deu), a regra virou
     * mais defensiva: (1) reconhece pagamento/liquidação de fatura pela categoria estruturada
     * que a própria Pluggy manda ("Credit card payment", já mapeada em
     * PluggyCategoryTranslator pra "Pagamento de fatura de cartão") somada a uma lista dos
     * textos reais confirmados acima; (2) só importa como RECEITA (estorno/crédito de verdade)
     * quando a descrição contém uma palavra de estorno ("estorno", "cancelamento",
     * "reembolso") — é assim que todo estorno real observado até agora se anuncia; (3) qualquer
     * transação negativa que não bate com nenhuma das duas regras é ignorada por segurança (não
     * vira Entry) e gera um log de aviso, em vez de arriscar contar um pagamento desconhecido
     * como renda — mesmo padrão usado pra fechar os buracos da tradução de categoria: loga o
     * caso real desconhecido pra decidir com dado de verdade depois, ao invés de adivinhar.
     * Não tenta parear automaticamente estorno com a compra original (mesmo motivo que a
     * heurística de parcelamento por repetição não foi implementada — ver
     * parseInstallmentFromDescription) — fica como dois lançamentos separados.
     *
     * @return quantos lançamentos novos foram importados (compras e estornos/créditos).
     */
    public int importForCreditCard(AppUser user, CreditCard creditCard, String pluggyAccountId, LocalDate from, LocalDate to) {
        List<PluggyClient.TransactionInfo> transactions = pluggyClient.listTransactions(pluggyAccountId, from, to);
        int imported = 0;
        for (PluggyClient.TransactionInfo tx : transactions) {
            if (!isUsableCardTransaction(tx) || tx.amount().compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            logInstallmentMetadataIfPresent(pluggyAccountId, tx);

            String description = resolveDescription(tx);

            if (tx.amount().compareTo(BigDecimal.ZERO) < 0) {
                if (isInvoiceSettlementTransaction(tx, description)) {
                    recordInvoiceSettlement(creditCard, tx, description);
                    continue;
                }
                if (!looksLikeGenuineCredit(description)) {
                    log.warn("Transação de cartão negativa não reconhecida (nem pagamento/financiamento "
                                    + "de fatura conhecido, nem estorno) — accountId={} desc='{}' amount={} "
                                    + "date={} category={}. Ignorada por segurança (nenhum Entry criado); "
                                    + "ajustar isInvoiceSettlementTransaction/looksLikeGenuineCredit com "
                                    + "esse dado real.",
                            pluggyAccountId, description, tx.amount(), tx.date(), tx.category());
                    continue;
                }
                if (importCreditOrRefund(user, creditCard, tx, description)) {
                    imported++;
                }
                continue;
            }

            LocalDate dueDate = creditCard.calculateInvoiceDueDate(tx.date());

            if (entryRepository.existsByOwnerIdAndDescriptionAndDueDateAndAmount(user.getId(), description, dueDate, tx.amount())) {
                continue;
            }

            Category category = resolveCategory(user, tx.category(), EntryType.DESPESA);

            Entry.EntryBuilder entryBuilder = Entry.builder()
                    .owner(user)
                    .description(description)
                    .amount(tx.amount())
                    .dueDate(dueDate)
                    .type(EntryType.DESPESA)
                    .status(EntryStatus.PENDENTE)
                    .origin(EntryOrigin.IMPORTADO_CARTAO)
                    .category(category)
                    .creditCard(creditCard);

            // Compra parcelada: guarda "parcela X de Y" direto no Entry (sem InstallmentPlan,
            // que é exclusivo do parcelamento manual — ver Entry.installmentsCount). Isso é o
            // que faz a tela de Lançamentos mostrar "(5/12)" pra compras importadas da Pluggy.
            //
            // Achado em produção (30/09, com export real do banco): pros três bancos do Marcio
            // (C6, Bradesco, Nubank), o creditCardMetadata da Pluggy NUNCA veio preenchido em
            // nenhuma das 336 transações de cartão de 2026 — então esse ramo abaixo, sozinho,
            // não resolve nada com dado real (fica pronto pro dia em que a Pluggy mandar isso
            // preenchido). O que resolve de verdade pro Nubank: a Pluggy manda a parcela como
            // TEXTO no fim da própria descrição da transação (ex.: "Pmz Distribuidora 2/4",
            // "MP *CASABULHOESCE 8/10") — não achei isso documentado, é comportamento real
            // observado. C6 e Bradesco não têm nenhum sinal (nem metadata nem texto) — só dá
            // pra saber que uma compra é parcelada lá olhando a mesma descrição+valor se repetir
            // em faturas consecutivas, o que também pega assinatura/tarifa recorrente de verdade
            // (ex.: "Tarifa Anuidade Diferenciada" R$98 todo mês) — arriscado demais pra inferir
            // sozinho sem confirmar com o Marcio, então não implementado ainda.
            PluggyClient.CreditCardMetadataInfo metadata = tx.creditCardMetadata();
            if (metadata != null && metadata.totalInstallments() != null && metadata.totalInstallments() > 1) {
                entryBuilder.installmentNumber(metadata.installmentNumber())
                        .installmentsCount(metadata.totalInstallments());
            } else {
                InstallmentFromDescription fromDescription = parseInstallmentFromDescription(description);
                if (fromDescription != null) {
                    entryBuilder.installmentNumber(fromDescription.number())
                            .installmentsCount(fromDescription.total());
                }
            }

            Entry entry = entryBuilder.build();
            entryRepository.save(entry);
            imported++;
        }
        return imported;
    }

    /** Extrato de conta: só aceita transações já concluídas (POSTED) — PENDING pode mudar ou cancelar. */
    private boolean isUsableAccountTransaction(PluggyClient.TransactionInfo tx) {
        return "POSTED".equals(tx.status()) && tx.amount() != null && tx.date() != null;
    }

    /**
     * Compras de cartão: aceita POSTED e PENDING. Segundo docs.pluggy.ai/docs/transactions,
     * transações da fatura ainda aberta (e parcelas futuras de uma compra parcelada) vêm como
     * PENDING em vez de POSTED — diferente de uma conta bancária, aqui a compra já aconteceu de
     * verdade assim que é feita, então não faz sentido esperar a fatura fechar pra importar (o
     * Entry já nasce PENDENTE de qualquer forma, é assim que o cartão sempre funcionou no
     * FinanceCash). Ainda não confirmado com dado real do Marcio — só com a documentação.
     */
    private boolean isUsableCardTransaction(PluggyClient.TransactionInfo tx) {
        return ("POSTED".equals(tx.status()) || "PENDING".equals(tx.status()))
                && tx.amount() != null && tx.date() != null;
    }

    /** Categoria que a Pluggy manda (em inglês) pra pagamento de fatura de cartão. */
    private static final String CREDIT_CARD_PAYMENT_CATEGORY = "credit card payment";

    /**
     * Prefixo da categoria que a Pluggy manda (em inglês) pra transferência entre as próprias
     * contas do usuário — cobre o nível 1 ("Same person transfer") e os níveis 2 ("Same person
     * transfer - Cash/Pix/Ted"), todos com esse mesmo prefixo. Achado em produção (01/10,
     * terceira rodada): dinheiro mudando de bolso, não é gasto nem receita — ver
     * importForAccount.
     */
    private static final String OWN_ACCOUNT_TRANSFER_CATEGORY_PREFIX = "same person transfer";

    /**
     * Categorias que a Pluggy manda (em inglês) pra transferência genérica/PIX — achado em
     * produção (01/10, quarta rodada): o Marcio confirmou que, no caso dele, praticamente tudo
     * que cai nessas categorias em setembro era repasse pessoal (empréstimo com a esposa,
     * dinheiro de um amigo pra comprar remédio pra ele) — R$13.670,61 de receita e R$3.106,76 de
     * despesa num mês só, sem ser renda/gasto real. Ele topou excluir por padrão, ciente do
     * risco de esconder uma renda real que um dia venha categorizada como "Transferências"
     * genérico — nesse caso o marcador manual (ver EntryService.excludeFromTotals/
     * includeInTotals) serve pra reverter caso a caso.
     */
    private static final List<String> GENERIC_TRANSFER_CATEGORY_MARKERS = List.of(
            "transfers", "transfer - pix"
    );

    /**
     * true quando o lançamento não deve contar no totalizador de despesa/receita (ver
     * EntryRepository.sumByFiltersAccrual), mas continua aparecendo na lista de Lançamentos — ver
     * javadoc de importForAccount pros casos automáticos (transferência própria, transferência
     * genérica/PIX e pagamento de fatura pelo lado da conta). Além disso, o usuário pode marcar
     * qualquer lançamento manualmente (ver EntryService.excludeFromTotals) pra casos que nenhum
     * sinal do banco identifica, como repasse/empréstimo pessoal.
     */
    private boolean isExcludedFromTotals(PluggyClient.TransactionInfo tx, String description) {
        String category = tx.category() != null ? tx.category().trim().toLowerCase(Locale.ROOT) : null;
        if (category != null && category.startsWith(OWN_ACCOUNT_TRANSFER_CATEGORY_PREFIX)) {
            return true;
        }
        if (category != null && GENERIC_TRANSFER_CATEGORY_MARKERS.contains(category)) {
            return true;
        }
        return isInvoiceSettlementTransaction(tx, description);
    }

    /**
     * Textos reais (confirmados em produção, 01/10, nos 3 bancos do Marcio) usados por cada
     * banco pra registrar a liquidação/financiamento da fatura — nenhum deles diz literalmente
     * "pagamento de fatura". Ver javadoc de importForCreditCard pra detalhe de cada um.
     */
    private static final List<String> INVOICE_SETTLEMENT_DESCRIPTION_MARKERS = List.of(
            "pagamento de fatura",
            "pagto. por deb em c/c",
            "pagto por deb em c/c",
            "inclusao de pagamento",
            "inclusão de pagamento",
            "pagamento ent parcelamento",
            "credito de refinanciamento",
            "crédito de refinanciamento",
            "pagamento recebido"
    );

    /**
     * Palavras que indicam estorno/crédito real de um item da fatura (não liquidação da fatura
     * inteira) — é assim que todo estorno real observado até agora se anuncia (ex.: "Estorno
     * Tarifa Anuidade Diferenciada").
     */
    private static final List<String> GENUINE_CREDIT_DESCRIPTION_MARKERS = List.of(
            "estorno", "cancelamento", "reembolso"
    );

    /**
     * true quando a transação negativa é liquidação/financiamento da fatura (transferência, não
     * um evento a registrar por si só) — reconhecida pela categoria estruturada da Pluggy
     * (mais confiável, não depende do texto de cada banco) OU por um dos textos reais já
     * confirmados. Ver javadoc de importForCreditCard.
     */
    private boolean isInvoiceSettlementTransaction(PluggyClient.TransactionInfo tx, String description) {
        if (tx.category() != null && tx.category().trim().equalsIgnoreCase(CREDIT_CARD_PAYMENT_CATEGORY)) {
            return true;
        }
        if (description == null) {
            return false;
        }
        String lower = description.toLowerCase(Locale.ROOT);
        return INVOICE_SETTLEMENT_DESCRIPTION_MARKERS.stream().anyMatch(lower::contains);
    }

    /** true quando a descrição indica um estorno/crédito real de item, não liquidação de fatura. */
    private boolean looksLikeGenuineCredit(String description) {
        if (description == null) {
            return false;
        }
        String lower = description.toLowerCase(Locale.ROOT);
        return GENUINE_CREDIT_DESCRIPTION_MARKERS.stream().anyMatch(lower::contains);
    }

    /**
     * Guarda a liquidação/financiamento de fatura reconhecida (ver isInvoiceSettlementTransaction)
     * como InvoiceSettlement, pra conferência automática contra o total calculado a partir das
     * compras importadas (ver EntryService.search / EntryTotalsDTO) — achado em produção (01/10)
     * comparando saldo líquido por cartão/mês com o app do C6. Dedup pelo id da transação na
     * Pluggy (mais confiável que description+amount+date, que o Entry usa).
     */
    private void recordInvoiceSettlement(CreditCard creditCard, PluggyClient.TransactionInfo tx, String description) {
        if (tx.id() != null
                && invoiceSettlementRepository.existsByCreditCardIdAndPluggyTransactionId(creditCard.getId(), tx.id())) {
            return;
        }
        LocalDate cycleDueDate = creditCard.calculateInvoiceDueDate(tx.date());
        InvoiceSettlement settlement = InvoiceSettlement.builder()
                .creditCard(creditCard)
                .cycleDueDate(cycleDueDate)
                .amount(tx.amount().abs())
                .transactionDate(tx.date())
                .description(description)
                .pluggyTransactionId(tx.id() != null ? tx.id() : description + "|" + tx.date() + "|" + tx.amount())
                .build();
        invoiceSettlementRepository.save(settlement);
    }

    /**
     * Estorno/crédito na fatura do cartão (amount negativo, exceto pagamento de fatura — ver
     * isInvoicePaymentDescription). Vira um Entry RECEITA PENDENTE separado, sem tentar parear
     * com a compra original que ele talvez esteja cancelando (ver javadoc de
     * importForCreditCard).
     *
     * @return true se um novo Entry foi salvo (false se já existia, por dedup).
     */
    private boolean importCreditOrRefund(AppUser user, CreditCard creditCard, PluggyClient.TransactionInfo tx, String description) {
        BigDecimal amount = tx.amount().abs();
        LocalDate dueDate = creditCard.calculateInvoiceDueDate(tx.date());

        if (entryRepository.existsByOwnerIdAndDescriptionAndDueDateAndAmount(user.getId(), description, dueDate, amount)) {
            return false;
        }

        Category category = resolveCategory(user, tx.category(), EntryType.RECEITA);

        Entry entry = Entry.builder()
                .owner(user)
                .description(description)
                .amount(amount)
                .dueDate(dueDate)
                .type(EntryType.RECEITA)
                .status(EntryStatus.PENDENTE)
                .origin(EntryOrigin.IMPORTADO_CARTAO)
                .category(category)
                .creditCard(creditCard)
                .build();
        entryRepository.save(entry);
        return true;
    }

    /**
     * Log temporário — remover depois de confirmar com dado real. O Marcio reportou (29/09) que
     * parcelamentos de cartão não apareceram todos na importação; a hipótese é que a Pluggy só
     * manda a parcela "atual" (não todas de uma vez) e/ou que estavam vindo como PENDING (agora
     * aceito, ver isUsableCardTransaction). Desde 30/09 o número/total de parcelas já é
     * persistido no Entry (ver importForCreditCard), então a tela de Lançamentos mostra "X/Y"
     * corretamente pra cada transação que a Pluggy manda — o que ainda falta confirmar é se ela
     * manda TODAS as parcelas futuras de uma vez ou só a atual. Se for só a atual, ainda
     * precisaremos sintetizar as parcelas futuras nós mesmos (como o InstallmentPlanService já
     * faz pra parcelamento manual) em vez de confiar só no que a Pluggy devolve — esse log
     * ajuda a decidir isso.
     */
    private void logInstallmentMetadataIfPresent(String pluggyAccountId, PluggyClient.TransactionInfo tx) {
        PluggyClient.CreditCardMetadataInfo metadata = tx.creditCardMetadata();
        if (metadata == null || metadata.totalInstallments() == null || metadata.totalInstallments() <= 1) {
            return;
        }
        log.info("Transação parcelada detectada — accountId={} desc='{}' date={} status={} "
                        + "installmentNumber={} totalInstallments={} purchaseDate={}",
                pluggyAccountId, tx.description(), tx.date(), tx.status(),
                metadata.installmentNumber(), metadata.totalInstallments(), metadata.purchaseDate());
    }

    private String resolveDescription(PluggyClient.TransactionInfo tx) {
        return (tx.description() != null && !tx.description().isBlank()) ? tx.description() : "Transação sem descrição";
    }

    /**
     * Padrão "N/M" no fim da descrição — como a Pluggy manda transações de cartão Nubank
     * parceladas (ex.: "Pmz Distribuidora 2/4"). Não achamos isso documentado na Pluggy; foi
     * observado com dado real exportado do banco do Marcio (30/09) depois que confirmamos que
     * creditCardMetadata nunca vem preenchido pros bancos dele. A descrição do Entry NÃO é
     * limpa desse sufixo de propósito: entries já importadas antes desse fix ainda têm o "N/M"
     * salvo no description, e o dedup (existsByOwnerIdAndDescriptionAndDueDateAndAmount) compara
     * description literal — limpar o sufixo criaria duplicata na próxima sincronização em vez
     * de reconhecer a transação já importada.
     */
    private static final Pattern DESCRIPTION_INSTALLMENT_PATTERN =
            Pattern.compile("(?:^|\\s)(\\d{1,2})\\s*/\\s*(\\d{1,2})\\s*$");

    private InstallmentFromDescription parseInstallmentFromDescription(String description) {
        if (description == null) {
            return null;
        }
        Matcher matcher = DESCRIPTION_INSTALLMENT_PATTERN.matcher(description);
        if (!matcher.find()) {
            return null;
        }
        int number = Integer.parseInt(matcher.group(1));
        int total = Integer.parseInt(matcher.group(2));
        // total>=2 (senão não é parcelamento) e number entre 1 e total, senão provavelmente é
        // outra coisa (ex.: um código de loja "LJ 12/34") coincidindo com o padrão.
        if (total < 2 || number < 1 || number > total) {
            return null;
        }
        return new InstallmentFromDescription(number, total);
    }

    private record InstallmentFromDescription(int number, int total) {
    }

    /**
     * Usa a categoria da Pluggy quando disponível, traduzida pra português (ver
     * PluggyCategoryTranslator — achado em produção 30/09: ao contrário do que a doc dizia, o
     * campo category vem preenchido mesmo no plano gratuito do Marcio, só que em inglês); senão
     * cai numa categoria genérica.
     */
    private Category resolveCategory(AppUser user, String pluggyCategoryName, EntryType type) {
        String categoryName = (pluggyCategoryName != null && !pluggyCategoryName.isBlank())
                ? PluggyCategoryTranslator.translate(pluggyCategoryName)
                : FALLBACK_CATEGORY_NAME;
        CategoryType categoryType = type == EntryType.RECEITA ? CategoryType.RECEITA : CategoryType.DESPESA;

        return categoryRepository.findByOwnerIdAndNameIgnoreCase(user.getId(), categoryName)
                .orElseGet(() -> categoryRepository.save(Category.builder()
                        .owner(user)
                        .name(categoryName)
                        .type(categoryType)
                        .active(true)
                        .build()));
    }
}
