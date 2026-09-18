package br.com.financecash.application.service;

import br.com.financecash.domain.model.*;
import br.com.financecash.domain.repository.EntryRepository;
import br.com.financecash.domain.repository.RecurringRuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Núcleo do "provisionamento de lançamentos futuros" citado no pedido original:
 * projeta, para cada RecurringRule ativa, os lançamentos dos próximos meses que
 * ainda não existem como Entry, usando o valor vigente do RecurringRuleValueHistory
 * na data de cada ocorrência (o que cobre reajustes anuais/semestrais automaticamente).
 *
 * Hoje é disparado sob demanda via endpoint (POST /api/recurring-rules/generate).
 * Na Fase 3 do roadmap isso passa a rodar também via job agendado (@Scheduled).
 */
@Service
public class RecurringEntryGenerationService {

    private final RecurringRuleRepository recurringRuleRepository;
    private final EntryRepository entryRepository;

    public RecurringEntryGenerationService(RecurringRuleRepository recurringRuleRepository, EntryRepository entryRepository) {
        this.recurringRuleRepository = recurringRuleRepository;
        this.entryRepository = entryRepository;
    }

    /**
     * Gera lançamentos para todas as regras ativas do usuário, dos meses entre
     * `from` e `monthsAhead` meses à frente (inclusive o mês de `from`).
     *
     * @return quantidade de novos lançamentos criados (idempotente — não duplica
     *         lançamentos de um mês que já foram gerados antes).
     */
    @Transactional
    public int generateForUser(AppUser user, LocalDate from, int monthsAhead) {
        List<RecurringRule> activeRules = recurringRuleRepository.findByOwnerIdAndActiveTrueOrderByNameAsc(user.getId());

        int created = 0;
        for (RecurringRule rule : activeRules) {
            created += generateForRule(user, rule, from, monthsAhead);
        }
        return created;
    }

    private int generateForRule(AppUser user, RecurringRule rule, LocalDate from, int monthsAhead) {
        int created = 0;
        YearMonth start = YearMonth.from(from);

        for (int offset = 0; offset <= monthsAhead; offset++) {
            YearMonth targetMonth = start.plusMonths(offset);

            if (!occursInMonth(rule, targetMonth)) {
                continue;
            }

            LocalDate dueDate = dueDateFor(rule, targetMonth);

            if (rule.getStartDate().isAfter(dueDate)) continue;
            if (rule.getEndDate() != null && rule.getEndDate().isBefore(dueDate)) continue;

            boolean alreadyGenerated = entryRepository
                    .findByOwnerIdAndRecurringRuleIdOrderByDueDateDesc(user.getId(), rule.getId())
                    .stream()
                    .anyMatch(e -> e.getDueDate().equals(dueDate));
            if (alreadyGenerated) continue;

            BigDecimal amount = amountEffectiveOn(rule, dueDate);
            if (amount == null) continue;

            Entry entry = Entry.builder()
                    .owner(user)
                    .description(rule.getName())
                    .amount(amount)
                    .dueDate(dueDate)
                    .type(rule.getType())
                    .status(EntryStatus.PENDENTE)
                    .origin(EntryOrigin.RECORRENCIA)
                    .category(rule.getCategory())
                    .account(rule.getAccount())
                    .recurringRule(rule)
                    .build();

            entryRepository.save(entry);
            created++;
        }

        return created;
    }

    private boolean occursInMonth(RecurringRule rule, YearMonth month) {
        return switch (rule.getFrequency()) {
            case MENSAL -> true;
            case BIMESTRAL -> month.getMonthValue() % 2 == 0;
            case TRIMESTRAL -> month.getMonthValue() % 3 == 0;
            case SEMESTRAL, ANUAL -> rule.getReferenceMonths().contains(month.getMonthValue());
        };
    }

    private LocalDate dueDateFor(RecurringRule rule, YearMonth month) {
        int day = Math.min(rule.getDayOfMonth(), month.lengthOfMonth());
        return month.atDay(day);
    }

    private BigDecimal amountEffectiveOn(RecurringRule rule, LocalDate date) {
        Optional<RecurringRuleValueHistory> latestApplicable = rule.getValueHistory().stream()
                .filter(h -> !h.getEffectiveFrom().isAfter(date))
                .max(Comparator.comparing(RecurringRuleValueHistory::getEffectiveFrom));

        return latestApplicable.map(RecurringRuleValueHistory::getAmount).orElse(null);
    }
}
