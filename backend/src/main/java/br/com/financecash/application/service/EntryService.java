package br.com.financecash.application.service;

import br.com.financecash.application.dto.CreditCardTotalDTO;
import br.com.financecash.application.dto.EntryBatchOperationResult;
import br.com.financecash.application.dto.EntryCreateRequest;
import br.com.financecash.application.dto.EntryDTO;
import br.com.financecash.application.dto.EntryPageDTO;
import br.com.financecash.application.dto.EntryTotalsDTO;
import br.com.financecash.application.dto.InvoiceSettlementDTO;
import br.com.financecash.application.dto.PendingInvoiceConfirmationDTO;
import br.com.financecash.application.dto.TotalsRegime;
import br.com.financecash.domain.model.*;
import br.com.financecash.openfinance.CreditCardDisplayNameFormatter;
import br.com.financecash.domain.repository.AccountRepository;
import br.com.financecash.domain.repository.CategoryRepository;
import br.com.financecash.domain.repository.CreditCardRepository;
import br.com.financecash.domain.repository.EntryRepository;
import br.com.financecash.domain.repository.InvoiceSettlementRepository;
import br.com.financecash.exception.ResourceNotFoundException;
import br.com.financecash.security.CurrentUserProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class EntryService {

    private final EntryRepository entryRepository;
    private final CategoryRepository categoryRepository;
    private final AccountRepository accountRepository;
    private final CreditCardRepository creditCardRepository;
    private final InvoiceSettlementRepository invoiceSettlementRepository;
    private final CurrentUserProvider currentUserProvider;

    public EntryService(EntryRepository entryRepository, CategoryRepository categoryRepository,
                         AccountRepository accountRepository, CreditCardRepository creditCardRepository,
                         InvoiceSettlementRepository invoiceSettlementRepository,
                         CurrentUserProvider currentUserProvider) {
        this.entryRepository = entryRepository;
        this.categoryRepository = categoryRepository;
        this.accountRepository = accountRepository;
        this.creditCardRepository = creditCardRepository;
        this.invoiceSettlementRepository = invoiceSettlementRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional(readOnly = true)
    public List<EntryDTO> listBetween(LocalDate from, LocalDate to) {
        AppUser user = currentUserProvider.getCurrentUser();
        return entryRepository.findByOwnerIdAndDueDateBetweenOrderByDueDateAsc(user.getId(), from, to)
                .stream().map(EntryDTO::from).toList();
    }

    /**
     * Busca paginada da tela de Lançamentos — todos os filtros além do período são opcionais
     * (null = não filtra por esse campo). {@code description} faz busca "contém",
     * case-insensitive. A lista em si é sempre por dueDate, independente do {@code regime} — só
     * o totalizador (ver buildTotals) muda de comportamento entre competência e caixa.
     */
    @Transactional(readOnly = true)
    public EntryPageDTO search(LocalDate dueDateFrom, LocalDate dueDateTo, UUID categoryId, EntryStatus status,
                                EntryOrigin origin, UUID creditCardId, String bankName, String description,
                                TotalsRegime regime, int page, int size) {
        AppUser user = currentUserProvider.getCurrentUser();
        String normalizedDescription = (description != null && !description.isBlank())
                ? description.trim().toLowerCase()
                : null;
        String normalizedBankName = (bankName != null && !bankName.isBlank()) ? bankName.trim() : null;
        TotalsRegime effectiveRegime = regime != null ? regime : TotalsRegime.COMPETENCIA;
        Pageable pageable = PageRequest.of(Math.max(page, 0), size <= 0 ? 50 : size, Sort.by(Sort.Direction.ASC, "dueDate"));

        Page<Entry> result = entryRepository.search(
                user.getId(), dueDateFrom, dueDateTo, categoryId, status, origin, creditCardId, normalizedBankName,
                normalizedDescription, pageable);
        EntryTotalsDTO totals = buildTotals(user.getId(), dueDateFrom, dueDateTo, categoryId, status, origin,
                creditCardId, normalizedBankName, normalizedDescription, effectiveRegime);
        return EntryPageDTO.from(result, totals);
    }

    /**
     * Totais + conferência com o banco pro mesmo filtro de search() — a pedido do Marcio
     * (01/10): a tela de Lançamentos já tinha filtro por cartão/período, mas nenhum totalizador
     * pra conferir contra o valor real de uma fatura. {@code bankSettlements} só é preenchido
     * quando o filtro é por um único cartão (senão não faz sentido comparar "banco informou" —
     * cada cartão tem sua própria fatura).
     *
     * <p><strong>Quinta rodada (01/10, a pedido do Marcio)</strong>: totais agora respeitam
     * {@code regime} (COMPETENCIA = comportamento original, tudo pelo dueDate; CAIXA = só
     * dinheiro que já saiu/entrou de verdade — ver TotalsRegime) e vêm acompanhados de
     * {@code creditCardTotals}, a quebra por cartão (o frontend agrupa por bankName pra mostrar
     * "por banco") — sem remover os totais gerais, só detalhando ("não esconde nada de você").
     */
    private EntryTotalsDTO buildTotals(UUID ownerId, LocalDate dueDateFrom, LocalDate dueDateTo, UUID categoryId,
                                        EntryStatus status, EntryOrigin origin, UUID creditCardId, String bankName,
                                        String normalizedDescription, TotalsRegime regime) {
        BigDecimal totalDespesa;
        BigDecimal totalReceita;
        long entryCount;
        List<CreditCardTotalDTO> creditCardTotals;

        if (regime == TotalsRegime.CAIXA) {
            EntryRepository.EntryTotalsProjection contaTotals = entryRepository.sumByFiltersCash(
                    ownerId, dueDateFrom, dueDateTo, categoryId, origin, creditCardId, bankName, normalizedDescription);
            List<InvoiceSettlementRepository.CreditCardCashTotalProjection> cardTotals = invoiceSettlementRepository
                    .sumByCreditCardCash(ownerId, dueDateFrom, dueDateTo, creditCardId, bankName);

            BigDecimal cardDespesa = cardTotals.stream()
                    .map(InvoiceSettlementRepository.CreditCardCashTotalProjection::getDespesa)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            totalDespesa = contaTotals.getTotalDespesa().add(cardDespesa);
            totalReceita = contaTotals.getTotalReceita();
            entryCount = contaTotals.getEntryCount();
            creditCardTotals = cardTotals.stream()
                    .map(p -> new CreditCardTotalDTO(
                            p.getCreditCardId(),
                            CreditCardDisplayNameFormatter.format(p.getBankName(), p.getBrand()),
                            CreditCardDisplayNameFormatter.shortBankName(p.getBankName()),
                            p.getDespesa(),
                            BigDecimal.ZERO))
                    .toList();
        } else {
            EntryRepository.EntryTotalsProjection totals = entryRepository.sumByFiltersAccrual(
                    ownerId, dueDateFrom, dueDateTo, categoryId, status, origin, creditCardId, bankName, normalizedDescription);
            List<EntryRepository.CreditCardTotalProjection> cardTotals = entryRepository.sumCardTotalsAccrual(
                    ownerId, dueDateFrom, dueDateTo, categoryId, status, origin, creditCardId, bankName, normalizedDescription);

            totalDespesa = totals.getTotalDespesa();
            totalReceita = totals.getTotalReceita();
            entryCount = totals.getEntryCount();
            creditCardTotals = cardTotals.stream()
                    .map(p -> new CreditCardTotalDTO(
                            p.getCreditCardId(),
                            CreditCardDisplayNameFormatter.format(p.getBankName(), p.getBrand()),
                            CreditCardDisplayNameFormatter.shortBankName(p.getBankName()),
                            p.getDespesa(),
                            p.getReceita()))
                    .toList();
        }

        List<InvoiceSettlementDTO> bankSettlements = creditCardId != null
                ? invoiceSettlementRepository.findByCreditCardIdAndCycleDueDateBetween(creditCardId, dueDateFrom, dueDateTo)
                        .stream().map(InvoiceSettlementDTO::from).toList()
                : List.of();

        return new EntryTotalsDTO(regime, totalDespesa, totalReceita, totalDespesa.subtract(totalReceita),
                entryCount, creditCardTotals, bankSettlements);
    }

    @Transactional
    public EntryDTO create(EntryCreateRequest request) {
        AppUser user = currentUserProvider.getCurrentUser();

        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Categoria não encontrada: " + request.categoryId()));

        Account account = request.accountId() != null
                ? accountRepository.findById(request.accountId())
                    .orElseThrow(() -> new ResourceNotFoundException("Conta não encontrada: " + request.accountId()))
                : null;

        CreditCard creditCard = request.creditCardId() != null
                ? creditCardRepository.findById(request.creditCardId())
                    .orElseThrow(() -> new ResourceNotFoundException("Cartão não encontrado: " + request.creditCardId()))
                : null;

        Entry entry = Entry.builder()
                .owner(user)
                .description(request.description())
                .amount(request.amount())
                .dueDate(request.dueDate())
                .type(request.type())
                .status(EntryStatus.PENDENTE)
                .origin(EntryOrigin.MANUAL)
                .category(category)
                .account(account)
                .creditCard(creditCard)
                .build();

        return EntryDTO.from(entryRepository.save(entry));
    }

    @Transactional
    public EntryDTO markAsPaid(UUID entryId, LocalDate paymentDate) {
        Entry entry = entryRepository.findById(entryId)
                .orElseThrow(() -> new ResourceNotFoundException("Lançamento não encontrado: " + entryId));
        entry.markAsPaid(paymentDate != null ? paymentDate : LocalDate.now());
        return EntryDTO.from(entry);
    }

    /** Desfaz um "marcar pago" feito sem querer. */
    @Transactional
    public EntryDTO markAsPending(UUID entryId) {
        Entry entry = entryRepository.findById(entryId)
                .orElseThrow(() -> new ResourceNotFoundException("Lançamento não encontrado: " + entryId));
        entry.markAsPending();
        return EntryDTO.from(entry);
    }

    /**
     * Marca manualmente um lançamento como "fora do total" — pra casos que nenhum sinal do
     * banco identifica sozinho (ex.: dinheiro repassado de um amigo pra comprar remédio pra
     * ele, empréstimo entre o usuário e a esposa) — a pedido do Marcio (01/10), depois de ver
     * que a exclusão automática (transferência própria / pagamento de fatura) não cobre esse
     * tipo de repasse pessoal. Continua aparecendo normalmente na lista de Lançamentos.
     */
    @Transactional
    public EntryDTO excludeFromTotals(UUID entryId) {
        Entry entry = entryRepository.findById(entryId)
                .orElseThrow(() -> new ResourceNotFoundException("Lançamento não encontrado: " + entryId));
        entry.setExcludedFromTotals(true);
        return EntryDTO.from(entry);
    }

    /** Desfaz uma exclusão manual do totalizador feita sem querer (ver excludeFromTotals). */
    @Transactional
    public EntryDTO includeInTotals(UUID entryId) {
        Entry entry = entryRepository.findById(entryId)
                .orElseThrow(() -> new ResourceNotFoundException("Lançamento não encontrado: " + entryId));
        entry.setExcludedFromTotals(false);
        return EntryDTO.from(entry);
    }

    @Transactional
    public void delete(UUID entryId) {
        if (!entryRepository.existsById(entryId)) {
            throw new ResourceNotFoundException("Lançamento não encontrado: " + entryId);
        }
        entryRepository.deleteById(entryId);
    }

    /**
     * Marca vários lançamentos como pagos de uma vez (Fase 1 do roadmap: "edição em
     * lote"). ids que não existem ou não pertencem ao usuário logado entram em
     * notFound() em vez de derrubar a operação inteira — ver Javadoc de
     * EntryBatchOperationResult.
     */
    @Transactional
    public EntryBatchOperationResult batchMarkAsPaid(List<UUID> ids, LocalDate paymentDate) {
        AppUser user = currentUserProvider.getCurrentUser();
        LocalDate effectiveDate = paymentDate != null ? paymentDate : LocalDate.now();

        List<UUID> notFound = new ArrayList<>();
        int affected = 0;
        for (UUID id : ids) {
            Entry entry = entryRepository.findById(id).orElse(null);
            if (entry == null || !entry.getOwner().getId().equals(user.getId())) {
                notFound.add(id);
                continue;
            }
            entry.markAsPaid(effectiveDate);
            affected++;
        }
        return new EntryBatchOperationResult(affected, notFound);
    }

    /** Exclui vários lançamentos de uma vez — mesma semântica de notFound() acima. */
    @Transactional
    public EntryBatchOperationResult batchDelete(List<UUID> ids) {
        AppUser user = currentUserProvider.getCurrentUser();

        List<UUID> notFound = new ArrayList<>();
        int affected = 0;
        for (UUID id : ids) {
            Entry entry = entryRepository.findById(id).orElse(null);
            if (entry == null || !entry.getOwner().getId().equals(user.getId())) {
                notFound.add(id);
                continue;
            }
            entryRepository.delete(entry);
            affected++;
        }
        return new EntryBatchOperationResult(affected, notFound);
    }

    /**
     * Lista faturas de cartão fechadas (vencimento antes de {@code referenceDate}) que ainda
     * têm compras PENDENTE — uma por (cartão, vencimento), pra alimentar o aviso "fatura tal
     * venceu, já foi paga?" no dashboard. Ver javadoc de PendingInvoiceConfirmationDTO pro
     * porquê disso ser confirmação manual, não automática.
     */
    @Transactional(readOnly = true)
    public List<PendingInvoiceConfirmationDTO> getPendingInvoiceConfirmations(LocalDate referenceDate) {
        AppUser user = currentUserProvider.getCurrentUser();
        List<Entry> overdueCardPurchases = entryRepository.findOverdueCardPurchases(user.getId(), referenceDate);

        Map<InvoiceKey, List<Entry>> byInvoice = overdueCardPurchases.stream()
                .collect(Collectors.groupingBy(
                        e -> new InvoiceKey(e.getCreditCard().getId(), e.getDueDate()),
                        LinkedHashMap::new,
                        Collectors.toList()));

        return byInvoice.values().stream()
                .map(entries -> {
                    Entry first = entries.get(0);
                    CreditCard card = first.getCreditCard();
                    BigDecimal total = entries.stream().map(Entry::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
                    return new PendingInvoiceConfirmationDTO(
                            card.getId(), card.getName(), card.getBankName(),
                            CreditCardDisplayNameFormatter.format(card.getBankName(), card.getBrand()),
                            first.getDueDate(), entries.size(), total);
                })
                .toList();
    }

    /**
     * Confirma que uma fatura de cartão inteira (todas as compras PENDENTE daquele cartão com
     * aquele vencimento) foi paga — a ação por trás do aviso de getPendingInvoiceConfirmations.
     * Sem {@code paymentDate}, usa o próprio dueDate como data de pagamento (melhor palpite; a
     * Pluggy não informa quando o usuário realmente pagou, só que a fatura fechou).
     */
    @Transactional
    public EntryBatchOperationResult confirmInvoicePaid(UUID creditCardId, LocalDate dueDate, LocalDate paymentDate) {
        AppUser user = currentUserProvider.getCurrentUser();
        LocalDate effectiveDate = paymentDate != null ? paymentDate : dueDate;

        List<Entry> entries = entryRepository.findByOwnerIdAndCreditCardIdAndDueDateAndOriginAndStatus(
                user.getId(), creditCardId, dueDate, EntryOrigin.IMPORTADO_CARTAO, EntryStatus.PENDENTE);
        for (Entry entry : entries) {
            entry.markAsPaid(effectiveDate);
        }
        return new EntryBatchOperationResult(entries.size(), List.of());
    }

    private record InvoiceKey(UUID creditCardId, LocalDate dueDate) {
    }
}
