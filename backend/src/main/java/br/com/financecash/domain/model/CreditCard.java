package br.com.financecash.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

/** Cartão de crédito vinculado a um banco/emissor. */
@Entity
@Table(name = "credit_card")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreditCard {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser owner;

    @Column(nullable = false)
    private String name;

    @Column(name = "bank_name", nullable = false)
    private String bankName;

    @Column(name = "closing_day", nullable = false)
    private int closingDay;

    @Column(name = "due_day", nullable = false)
    private int dueDay;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    /**
     * Calcula em qual fatura (e portanto qual data de vencimento) uma compra feita em
     * {@code transactionDate} vai cair, a partir de closingDay/dueDay. Usado pelo
     * TransactionImportService para preencher o dueDate de lançamentos importados via
     * Open Finance (a Pluggy só traz a data da compra, não a data de vencimento da fatura).
     *
     * <p>Regra: se a compra foi feita até o dia de fechamento (inclusive) do mês dela, entra
     * na fatura que fecha nesse mês; senão, entra na fatura do mês seguinte. O vencimento é o
     * dueDay do mês de fechamento da fatura — exceto quando isso resultaria numa data igual ou
     * anterior ao fechamento (comum quando dueDay &lt; closingDay, ex.: fecha dia 25, vence dia
     * 5), caso em que o vencimento vai para o mês seguinte ao fechamento. closingDay/dueDay são
     * ajustados ("clampados") para o último dia do mês quando o mês não tem dias suficientes
     * (ex.: fechamento dia 31 em fevereiro).</p>
     */
    public LocalDate calculateInvoiceDueDate(LocalDate transactionDate) {
        YearMonth transactionMonth = YearMonth.from(transactionDate);
        LocalDate closingDateThisMonth = clampDay(transactionMonth, closingDay);

        YearMonth cycleMonth;
        LocalDate closingDate;
        if (transactionDate.isAfter(closingDateThisMonth)) {
            cycleMonth = transactionMonth.plusMonths(1);
            closingDate = clampDay(cycleMonth, closingDay);
        } else {
            cycleMonth = transactionMonth;
            closingDate = closingDateThisMonth;
        }

        LocalDate dueDate = clampDay(cycleMonth, dueDay);
        if (!dueDate.isAfter(closingDate)) {
            dueDate = clampDay(cycleMonth.plusMonths(1), dueDay);
        }
        return dueDate;
    }

    private static LocalDate clampDay(YearMonth month, int day) {
        return month.atDay(Math.min(day, month.lengthOfMonth()));
    }
}
