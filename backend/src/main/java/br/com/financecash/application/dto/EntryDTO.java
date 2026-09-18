package br.com.financecash.application.dto;

import br.com.financecash.domain.model.Entry;
import br.com.financecash.domain.model.EntryOrigin;
import br.com.financecash.domain.model.EntryStatus;
import br.com.financecash.domain.model.EntryType;

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
        UUID installmentPlanId,
        Integer installmentNumber,
        Integer installmentsCount
) {
    public static EntryDTO from(Entry entry) {
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
                entry.getInstallmentPlan() != null ? entry.getInstallmentPlan().getId() : null,
                entry.getInstallmentNumber(),
                entry.getInstallmentPlan() != null ? entry.getInstallmentPlan().getInstallmentsCount() : null
        );
    }
}
