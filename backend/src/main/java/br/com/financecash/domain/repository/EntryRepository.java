package br.com.financecash.domain.repository;

import br.com.financecash.domain.model.Entry;
import br.com.financecash.domain.model.EntryOrigin;
import br.com.financecash.domain.model.EntryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface EntryRepository extends JpaRepository<Entry, UUID> {

    List<Entry> findByOwnerIdAndDueDateBetweenOrderByDueDateAsc(UUID ownerId, LocalDate from, LocalDate to);

    List<Entry> findByOwnerIdAndDueDateAndStatusNotOrderByDescriptionAsc(UUID ownerId, LocalDate dueDate, EntryStatus excludedStatus);

    List<Entry> findByOwnerIdAndRecurringRuleIdOrderByDueDateDesc(UUID ownerId, UUID recurringRuleId);

    List<Entry> findByOwnerIdAndInstallmentPlanIdOrderByInstallmentNumberAsc(UUID ownerId, UUID installmentPlanId);

    /** Usado pela importação em lote (EntryImportService) para pular linhas já importadas antes. */
    boolean existsByOwnerIdAndDescriptionAndDueDateAndAmount(UUID ownerId, String description, LocalDate dueDate, BigDecimal amount);

    @Query("""
            select e from Entry e
            where e.owner.id = :ownerId
              and e.status = 'PENDENTE'
              and e.dueDate between :from and :to
            """)
    List<Entry> findPendingBetween(@Param("ownerId") UUID ownerId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * Busca paginada da tela de Lançamentos, com todos os filtros opcionais (null = "não
     * filtra por esse campo"). {@code description} já vem normalizada (minúsculo, sem espaço
     * nas pontas) pelo EntryService — a comparação aqui usa lower()/like só do lado da coluna.
     */
    @Query("""
            select e from Entry e
            where e.owner.id = :ownerId
              and e.dueDate between :dueDateFrom and :dueDateTo
              and (:categoryId is null or e.category.id = :categoryId)
              and (:status is null or e.status = :status)
              and (:origin is null or e.origin = :origin)
              and (:creditCardId is null or e.creditCard.id = :creditCardId)
              and (:bankName is null or lower(e.creditCard.bankName) = lower(cast(:bankName as string)))
              and (:description is null or lower(e.description) like concat('%', cast(:description as string), '%'))
            """)
    Page<Entry> search(
            @Param("ownerId") UUID ownerId,
            @Param("dueDateFrom") LocalDate dueDateFrom,
            @Param("dueDateTo") LocalDate dueDateTo,
            @Param("categoryId") UUID categoryId,
            @Param("status") EntryStatus status,
            @Param("origin") EntryOrigin origin,
            @Param("creditCardId") UUID creditCardId,
            @Param("bankName") String bankName,
            @Param("description") String description,
            Pageable pageable);

    /**
     * Totais (não paginados) do mesmo filtro de search(), em regime de competência (o
     * comportamento original: tudo pelo dueDate, cartão e conta somados juntos) — a pedido do
     * Marcio, 01/10: os filtros já existiam, faltava somar o resultado filtrado pra conferência,
     * ex. contra a fatura real de um cartão/mês. Ver sumByFiltersCash pro regime de caixa e
     * sumCardTotalsAccrual/InvoiceSettlementRepository.sumByCreditCardCash pra quebra por cartão
     * (quinta rodada, 01/10, a pedido do Marcio: "quero ver o totalizador por cartão e por
     * banco").
     */
    @Query("""
            select
                coalesce(sum(case when e.type = 'DESPESA' and e.excludedFromTotals = false then e.amount else 0 end), 0) as totalDespesa,
                coalesce(sum(case when e.type = 'RECEITA' and e.excludedFromTotals = false then e.amount else 0 end), 0) as totalReceita,
                count(e) as entryCount
            from Entry e
            where e.owner.id = :ownerId
              and e.dueDate between :dueDateFrom and :dueDateTo
              and (:categoryId is null or e.category.id = :categoryId)
              and (:status is null or e.status = :status)
              and (:origin is null or e.origin = :origin)
              and (:creditCardId is null or e.creditCard.id = :creditCardId)
              and (:bankName is null or lower(e.creditCard.bankName) = lower(cast(:bankName as string)))
              and (:description is null or lower(e.description) like concat('%', cast(:description as string), '%'))
            """)
    EntryTotalsProjection sumByFiltersAccrual(
            @Param("ownerId") UUID ownerId,
            @Param("dueDateFrom") LocalDate dueDateFrom,
            @Param("dueDateTo") LocalDate dueDateTo,
            @Param("categoryId") UUID categoryId,
            @Param("status") EntryStatus status,
            @Param("origin") EntryOrigin origin,
            @Param("creditCardId") UUID creditCardId,
            @Param("bankName") String bankName,
            @Param("description") String description);

    /**
     * Totais em regime de caixa: só lançamentos FORA de cartão (e.creditCard is null), já pagos
     * de verdade (status PAGO), pela data real de pagamento (paymentDate) — não pelo dueDate.
     * O que sai/entra de cartão não entra aqui: entra à parte, pelo que o banco informou como
     * pago/financiado por fatura (ver InvoiceSettlementRepository.sumByCreditCardCash), porque
     * Entry de cartão nasce PENDENTE e normalmente não tem paymentDate real preenchido. Se
     * {@code creditCardId}/{@code bankName} estiver preenchido, o resultado aqui vem zerado de
     * propósito (nada fora de cartão bate com um filtro de cartão/banco) — o valor de verdade
     * pra esse caso está no InvoiceSettlement. Decisão do Marcio (01/10, quinta rodada): regime
     * de caixa do cartão usa o valor que o banco informou, não o paymentDate de cada compra.
     */
    @Query("""
            select
                coalesce(sum(case when e.type = 'DESPESA' and e.excludedFromTotals = false then e.amount else 0 end), 0) as totalDespesa,
                coalesce(sum(case when e.type = 'RECEITA' and e.excludedFromTotals = false then e.amount else 0 end), 0) as totalReceita,
                count(e) as entryCount
            from Entry e
            where e.owner.id = :ownerId
              and e.creditCard is null
              and e.status = 'PAGO'
              and e.paymentDate between :from and :to
              and (:categoryId is null or e.category.id = :categoryId)
              and (:origin is null or e.origin = :origin)
              and (:creditCardId is null or e.creditCard.id = :creditCardId)
              and (:bankName is null or lower(e.creditCard.bankName) = lower(cast(:bankName as string)))
              and (:description is null or lower(e.description) like concat('%', cast(:description as string), '%'))
            """)
    EntryTotalsProjection sumByFiltersCash(
            @Param("ownerId") UUID ownerId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("categoryId") UUID categoryId,
            @Param("origin") EntryOrigin origin,
            @Param("creditCardId") UUID creditCardId,
            @Param("bankName") String bankName,
            @Param("description") String description);

    interface EntryTotalsProjection {
        BigDecimal getTotalDespesa();
        BigDecimal getTotalReceita();
        long getEntryCount();
    }

    /**
     * Quebra do totalizador por cartão, em regime de competência: soma DESPESA/RECEITA de cada
     * cartão (dueDate no período, sem os excluídos), agrupado por cartão — o "quero ver o
     * totalizador por cartão e por banco" do Marcio (01/10, quinta rodada). Agrupar por
     * cc.bankName no frontend dá o "por banco" (um usuário pode ter mais de um cartão do mesmo
     * banco). Mesmos filtros de sumByFiltersAccrual, exceto status (card breakdown mostra
     * PENDENTE e PAGO juntos, igual ao totalizador principal).
     */
    @Query("""
            select
                cc.id as creditCardId,
                cc.name as cardName,
                cc.bankName as bankName,
                cc.brand as brand,
                coalesce(sum(case when e.type = 'DESPESA' then e.amount else 0 end), 0) as despesa,
                coalesce(sum(case when e.type = 'RECEITA' then e.amount else 0 end), 0) as receita
            from Entry e join e.creditCard cc
            where e.owner.id = :ownerId
              and e.excludedFromTotals = false
              and e.dueDate between :dueDateFrom and :dueDateTo
              and (:categoryId is null or e.category.id = :categoryId)
              and (:status is null or e.status = :status)
              and (:origin is null or e.origin = :origin)
              and (:creditCardId is null or cc.id = :creditCardId)
              and (:bankName is null or lower(cc.bankName) = lower(cast(:bankName as string)))
              and (:description is null or lower(e.description) like concat('%', cast(:description as string), '%'))
            group by cc.id, cc.name, cc.bankName, cc.brand
            order by cc.bankName, cc.name
            """)
    List<CreditCardTotalProjection> sumCardTotalsAccrual(
            @Param("ownerId") UUID ownerId,
            @Param("dueDateFrom") LocalDate dueDateFrom,
            @Param("dueDateTo") LocalDate dueDateTo,
            @Param("categoryId") UUID categoryId,
            @Param("status") EntryStatus status,
            @Param("origin") EntryOrigin origin,
            @Param("creditCardId") UUID creditCardId,
            @Param("bankName") String bankName,
            @Param("description") String description);

    interface CreditCardTotalProjection {
        UUID getCreditCardId();
        String getCardName();
        String getBankName();
        String getBrand();
        BigDecimal getDespesa();
        BigDecimal getReceita();
    }

    @Query("""
            select e.category.id as categoryId, e.category.name as categoryName, e.category.colorHex as colorHex,
                   sum(e.amount) as total, count(e) as entryCount
            from Entry e
            where e.owner.id = :ownerId
              and e.type = 'DESPESA'
              and e.dueDate between :from and :to
            group by e.category.id, e.category.name, e.category.colorHex
            order by sum(e.amount) desc
            """)
    List<CategorySpendingProjection> sumSpendingByCategory(@Param("ownerId") UUID ownerId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    interface CategorySpendingProjection {
        UUID getCategoryId();
        String getCategoryName();
        String getColorHex();
        java.math.BigDecimal getTotal();
        long getEntryCount();
    }

    /**
     * Compras de cartão importadas (IMPORTADO_CARTAO) ainda PENDENTE cujo vencimento já passou
     * — candidatas a "confirmar fatura como paga" (ver EntryService.getPendingInvoiceConfirmations
     * e o achado de 01/10 em docs/OPEN-FINANCE-E-BOLETOS.md sobre por que isso não é automático).
     * Ordenado por cartão + vencimento pra facilitar agrupar em Java por (cartão, vencimento).
     */
    @Query("""
            select e from Entry e
            where e.owner.id = :ownerId
              and e.origin = 'IMPORTADO_CARTAO'
              and e.status = 'PENDENTE'
              and e.creditCard is not null
              and e.dueDate < :referenceDate
            order by e.creditCard.id, e.dueDate
            """)
    List<Entry> findOverdueCardPurchases(@Param("ownerId") UUID ownerId, @Param("referenceDate") LocalDate referenceDate);

    /** Usado por EntryService.confirmInvoicePaid pra achar exatamente os lançamentos de UMA fatura. */
    List<Entry> findByOwnerIdAndCreditCardIdAndDueDateAndOriginAndStatus(
            UUID ownerId, UUID creditCardId, LocalDate dueDate, EntryOrigin origin, EntryStatus status);
}
