package br.com.financecash.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Registro da liquidação/financiamento de uma fatura de cartão, conforme a própria Pluggy
 * informa (transação negativa reconhecida como "isso é pagamento da fatura, não um evento da
 * fatura em si" — ver TransactionImportService.isInvoiceSettlementTransaction). Diferente de um
 * Entry: não representa uma despesa/receita do usuário, é só o "valor que o banco disse que
 * você pagou/liquidou" pra essa fatura (cycleDueDate) — usado pra conferência automática contra
 * o valor calculado a partir das compras importadas (ver EntryService.search /
 * EntryTotalsDTO.bankSettlements), depois que o Marcio reportou "os valores por cartão estão
 * fora da realidade" comparando com o app do banco (01/10).
 *
 * <p>Guardado por transação da Pluggy (não colapsado num único valor por fatura): em alguns
 * meses o banco manda mais de uma transação de liquidação pro mesmo ciclo (ex.: financiamento
 * parcial da fatura + refinanciamento de saldo), e somar tudo cegamente pode dar valor errado
 * (visto em produção: o mesmo valor aparecendo duas vezes com descrições diferentes) — melhor
 * mostrar cada uma com sua descrição/data e deixar o usuário julgar do que arriscar um número
 * "mágico" errado.
 */
@Entity
@Table(name = "invoice_settlement",
        uniqueConstraints = @UniqueConstraint(columnNames = {"credit_card_id", "pluggy_transaction_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceSettlement {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "credit_card_id", nullable = false)
    private CreditCard creditCard;

    /** Ciclo/fatura a que essa liquidação pertence — mesmo cálculo de CreditCard.calculateInvoiceDueDate. */
    @Column(name = "cycle_due_date", nullable = false)
    private LocalDate cycleDueDate;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @Column(nullable = false)
    private String description;

    /** Id da transação na Pluggy — usado pro dedup (mais confiável que description+amount+date). */
    @Column(name = "pluggy_transaction_id", nullable = false)
    private String pluggyTransactionId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
