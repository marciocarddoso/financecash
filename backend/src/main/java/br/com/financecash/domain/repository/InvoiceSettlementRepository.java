package br.com.financecash.domain.repository;

import br.com.financecash.domain.model.InvoiceSettlement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface InvoiceSettlementRepository extends JpaRepository<InvoiceSettlement, UUID> {

    boolean existsByCreditCardIdAndPluggyTransactionId(UUID creditCardId, String pluggyTransactionId);

    /**
     * Liquidações de fatura de um cartão específico dentro de um período de ciclo — usada pela
     * busca de Lançamentos (EntryService.search) pra mostrar, ao lado do total calculado a
     * partir das compras importadas, o que o banco informou como pagamento/financiamento da
     * fatura pra esse mesmo cartão+período (conferência automática).
     */
    @Query("""
            select s from InvoiceSettlement s
            where s.creditCard.id = :creditCardId
              and s.cycleDueDate between :from and :to
            order by s.cycleDueDate, s.transactionDate
            """)
    List<InvoiceSettlement> findByCreditCardIdAndCycleDueDateBetween(
            @Param("creditCardId") UUID creditCardId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * Quebra por cartão do totalizador em regime de CAIXA (ver EntryService.buildTotals): soma
     * o que o banco informou como pago/financiado por cartão, pela mesma janela de ciclo
     * (cycleDueDate) usada no regime de competência — assim as duas visões cobrem exatamente o
     * mesmo período de fatura, só mudando o que consideram "o valor do cartão". Decisão do
     * Marcio (01/10, quinta rodada): regime de caixa usa o valor que o banco informou, não o
     * paymentDate de cada compra individual.
     */
    @Query("""
            select
                cc.id as creditCardId,
                cc.name as cardName,
                cc.bankName as bankName,
                cc.brand as brand,
                coalesce(sum(s.amount), 0) as despesa
            from InvoiceSettlement s join s.creditCard cc
            where cc.owner.id = :ownerId
              and s.cycleDueDate between :from and :to
              and (:creditCardId is null or cc.id = :creditCardId)
              and (:bankName is null or lower(cc.bankName) = lower(cast(:bankName as string)))
            group by cc.id, cc.name, cc.bankName, cc.brand
            order by cc.bankName, cc.name
            """)
    List<CreditCardCashTotalProjection> sumByCreditCardCash(
            @Param("ownerId") UUID ownerId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("creditCardId") UUID creditCardId,
            @Param("bankName") String bankName);

    interface CreditCardCashTotalProjection {
        UUID getCreditCardId();
        String getCardName();
        String getBankName();
        String getBrand();
        java.math.BigDecimal getDespesa();
    }
}
