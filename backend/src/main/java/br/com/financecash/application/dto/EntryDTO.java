package br.com.financecash.application.dto;

import br.com.financecash.domain.model.Entry;
import br.com.financecash.domain.model.EntryOrigin;
import br.com.financecash.domain.model.EntryStatus;
import br.com.financecash.domain.model.EntryType;
import br.com.financecash.openfinance.CreditCardDisplayNameFormatter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record EntryDTO(
        UUID id,
        String description,
        BigDecimal amount,
        LocalDate dueDate,
        LocalDate paymentDate,
        EntryType type,
        EntryStatus status,
        EntryOrigin origin,
        UUID categoryId,
        String categoryName,
        UUID accountId,
        String accountName,
        UUID creditCardId,
        String creditCardName,
        String creditCardBankName,
        String creditCardDisplayName,
        UUID installmentPlanId,
        Integer installmentNumber,
        Integer installmentsCount,
        boolean excludedFromTotals
) {
    public static EntryDTO from(Entry entry) {
        // Total de parcelas: prioriza o InstallmentPlan (parcelamento manual) quando existe;
        // senão cai no installmentsCount direto do Entry (compra parcelada importada da
        // Pluggy, que não tem InstallmentPlan — ver Entry.installmentsCount).
        //
        // Bug corrigido (30/09): um operador ternário com um lado `int` (primitivo, retorno de
        // InstallmentPlan.getInstallmentsCount()) e o outro `Integer` (Entry.getInstallmentsCount(),
        // que pode ser null) força o Java a fazer unboxing do lado Integer mesmo quando esse não
        // é o ramo escolhido em tempo de execução (JLS 15.25) — resultado: NullPointerException
        // sempre que installmentPlan era null E installmentsCount também era null (o caso mais
        // comum, lançamento sem nenhum parcelamento). Trocado por if/else, que não sofre dessa
        // promoção numérica.
        Integer installmentsCount;
        if (entry.getInstallmentPlan() != null) {
            installmentsCount = entry.getInstallmentPlan().getInstallmentsCount();
        } else {
            installmentsCount = entry.getInstallmentsCount();
        }

        return new EntryDTO(
                entry.getId(),
                entry.getDescription(),
                entry.getAmount(),
                entry.getDueDate(),
                entry.getPaymentDate(),
                entry.getType(),
                entry.getStatus(),
                entry.getOrigin(),
                entry.getCategory().getId(),
                entry.getCategory().getName(),
                entry.getAccount() != null ? entry.getAccount().getId() : null,
                entry.getAccount() != null ? entry.getAccount().getName() : null,
                entry.getCreditCard() != null ? entry.getCreditCard().getId() : null,
                entry.getCreditCard() != null ? entry.getCreditCard().getName() : null,
                entry.getCreditCard() != null ? entry.getCreditCard().getBankName() : null,
                entry.getCreditCard() != null
                        ? CreditCardDisplayNameFormatter.format(entry.getCreditCard().getBankName(), entry.getCreditCard().getBrand())
                        : null,
                entry.getInstallmentPlan() != null ? entry.getInstallmentPlan().getId() : null,
                entry.getInstallmentNumber(),
                installmentsCount,
                entry.isExcludedFromTotals()
        );
    }
}
