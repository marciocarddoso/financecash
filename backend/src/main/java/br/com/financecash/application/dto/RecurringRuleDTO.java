package br.com.financecash.application.dto;

import br.com.financecash.domain.model.EntryType;
import br.com.financecash.domain.model.RecurrenceFrequency;
import br.com.financecash.domain.model.RecurringRule;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record RecurringRuleDTO(
        UUID id,
        String name,
        EntryType type,
        UUID categoryId,
        String categoryName,
        RecurrenceFrequency frequency,
        int dayOfMonth,
        List<Integer> referenceMonths,
        LocalDate startDate,
        LocalDate endDate,
        boolean active,
        BigDecimal currentAmount
) {
    public static RecurringRuleDTO from(RecurringRule rule) {
        BigDecimal current = rule.getValueHistory().stream()
                .max(Comparator.comparing(h -> h.getEffectiveFrom()))
                .map(h -> h.getAmount())
                .orElse(BigDecimal.ZERO);

        return new RecurringRuleDTO(
                rule.getId(), rule.getName(), rule.getType(),
                rule.getCategory().getId(), rule.getCategory().getName(),
                rule.getFrequency(), rule.getDayOfMonth(), rule.getReferenceMonths(),
                rule.getStartDate(), rule.getEndDate(), rule.isActive(), current);
    }
}
