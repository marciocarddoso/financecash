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
import br.com.financecash.domain.repository.CategoryRepository;
import br.com.financecash.domain.repository.EntryRepository;
import br.com.financecash.openfinance.PluggyClient;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

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

    private static final String FALLBACK_CATEGORY_NAME = "Outros";

    private final PluggyClient pluggyClient;
    private final EntryRepository entryRepository;
    private final CategoryRepository categoryRepository;

    public TransactionImportService(PluggyClient pluggyClient, EntryRepository entryRepository,
                                     CategoryRepository categoryRepository) {
        this.pluggyClient = pluggyClient;
        this.entryRepository = entryRepository;
        this.categoryRepository = categoryRepository;
    }

    /**
     * Importa as transações de uma conta corrente/poupança já resolvida como Account. Cada
     * transação POSTED vira um Entry já PAGO (é um movimento que já aconteceu na conta).
     *
     * @return quantos lançamentos novos foram importados (duplicatas e transações PENDING são
     *         puladas silenciosamente, sem contar).
     */
    public int importForAccount(AppUser user, Account account, String pluggyAccountId, LocalDate from, LocalDate to) {
        List<PluggyClient.TransactionInfo> transactions = pluggyClient.listTransactions(pluggyAccountId, from, to);
        int imported = 0;
        for (PluggyClient.TransactionInfo tx : transactions) {
            if (!isUsable(tx) || tx.amount().compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }

            EntryType type = tx.amount().compareTo(BigDecimal.ZERO) > 0 ? EntryType.RECEITA : EntryType.DESPESA;
            BigDecimal amount = tx.amount().abs();
            String description = resolveDescription(tx);

            if (entryRepository.existsByOwnerIdAndDescriptionAndDueDateAndAmount(user.getId(), description, tx.date(), amount)) {
                continue;
            }

            Category category = resolveCategory(user, tx.category(), type);

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
                    .account(account)
                    .build();
            entryRepository.save(entry);
            imported++;
        }
        return imported;
    }

    /**
     * Importa as transações de um cartão de crédito já resolvido como CreditCard. Só considera
     * compras (amount positivo, na convenção da Pluggy para cartões) — pagamentos/estornos da
     * fatura (amount negativo) são ignorados, ver javadoc da classe. Cada compra vira um Entry
     * PENDENTE, com dueDate calculado a partir do fechamento/vencimento do cartão.
     *
     * @return quantos lançamentos novos foram importados.
     */
    public int importForCreditCard(AppUser user, CreditCard creditCard, String pluggyAccountId, LocalDate from, LocalDate to) {
        List<PluggyClient.TransactionInfo> transactions = pluggyClient.listTransactions(pluggyAccountId, from, to);
        int imported = 0;
        for (PluggyClient.TransactionInfo tx : transactions) {
            if (!isUsable(tx) || tx.amount().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            String description = resolveDescription(tx);
            LocalDate dueDate = creditCard.calculateInvoiceDueDate(tx.date());

            if (entryRepository.existsByOwnerIdAndDescriptionAndDueDateAndAmount(user.getId(), description, dueDate, tx.amount())) {
                continue;
            }

            Category category = resolveCategory(user, tx.category(), EntryType.DESPESA);

            Entry entry = Entry.builder()
                    .owner(user)
                    .description(description)
                    .amount(tx.amount())
                    .dueDate(dueDate)
                    .type(EntryType.DESPESA)
                    .status(EntryStatus.PENDENTE)
                    .origin(EntryOrigin.IMPORTADO_CARTAO)
                    .category(category)
                    .creditCard(creditCard)
                    .build();
            entryRepository.save(entry);
            imported++;
        }
        return imported;
    }

    private boolean isUsable(PluggyClient.TransactionInfo tx) {
        return "POSTED".equals(tx.status()) && tx.amount() != null && tx.date() != null;
    }

    private String resolveDescription(PluggyClient.TransactionInfo tx) {
        return (tx.description() != null && !tx.description().isBlank()) ? tx.description() : "Transação sem descrição";
    }

    /** Usa a categoria da Pluggy quando disponível (plano Pro+); senão cai numa categoria genérica. */
    private Category resolveCategory(AppUser user, String pluggyCategoryName, EntryType type) {
        String categoryName = (pluggyCategoryName != null && !pluggyCategoryName.isBlank())
                ? pluggyCategoryName
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
