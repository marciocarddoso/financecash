package br.com.financecash.application.dto;

import br.com.financecash.domain.model.InstallmentPlan;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record InstallmentPlanDTO(
        UUID id,
        String description,
        BigDecimal totalAmount,
        int installmentsCount,
        LocalDate firstDueDate,
        UUID categoryId,
        String categoryName
) {
    public static InstallmentPlanDTO from(InstallmentPlan plan) {
        return new InstallmentPlanDTO(
                plan.getId(), plan.getDescription(), plan.getTotalAmount(), plan.getInstallmentsCount(),
                plan.getFirstDueDate(), plan.getCategory().getId(), plan.getCategory().getName());
    }
}
