package br.com.financecash.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Lançamento — entidade central do domínio. Equivale a uma linha da planilha de
 * controle financeiro. Ver docs/MODELO-DOMINIO.md para a justificativa de design.
 */
@Entity
@Table(name = "entry")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Entry {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser owner;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "payment_date")
    private LocalDate paymentDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EntryType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private EntryStatus status = EntryStatus.PENDENTE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private EntryOrigin origin = EntryOrigin.MANUAL;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recurring_rule_id")
    private RecurringRule recurringRule;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "installment_plan_id")
    private InstallmentPlan installmentPlan;

    /**
     * Número da parcela (ex.: 3 de 12) — preenchido tanto pelo parcelamento manual
     * (installmentPlan != null) quanto pela importação de compras parceladas via Open
     * Finance (installmentPlan == null, ver installmentsCount abaixo).
     */
    @Column(name = "installment_number")
    private Integer installmentNumber;

    /**
     * Total de parcelas, usado só quando installmentPlan é null — ou seja, para compras
     * parceladas importadas da Pluggy, que não geram um InstallmentPlan (ver
     * TransactionImportService.importForCreditCard). Quando installmentPlan != null, o total
     * "oficial" é installmentPlan.getInstallmentsCount(); ver EntryDTO.from().
     */
    @Column(name = "installments_count")
    private Integer installmentsCount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "credit_card_id")
    private CreditCard creditCard;

    /**
     * true quando esse lançamento não deve entrar na soma de despesa/receita do totalizador
     * (ver EntryRepository.sumByFiltersAccrual) — mas continua aparecendo normalmente na lista de
     * Lançamentos, pra não esconder nada do usuário. Casos: transferência entre as próprias
     * contas do usuário, e o débito em conta que paga uma fatura de cartão já contada como
     * despesa quando a compra foi feita (ver TransactionImportService.importForAccount).
     */
    @Column(name = "excluded_from_totals", nullable = false)
    @Builder.Default
    private boolean excludedFromTotals = false;

    public boolean isOverdueAsOf(LocalDate referenceDate) {
        return status == EntryStatus.PENDENTE && dueDate.isBefore(referenceDate);
    }

    public void markAsPaid(LocalDate paymentDate) {
        this.paymentDate = paymentDate;
        this.status = EntryStatus.PAGO;
    }

    /** Desfaz um "marcar pago" feito sem querer — volta pra PENDENTE e limpa a data de pagamento. */
    public void markAsPending() {
        this.paymentDate = null;
        this.status = EntryStatus.PENDENTE;
    }
}
