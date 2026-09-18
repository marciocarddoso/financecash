package br.com.financecash.application.service;

import br.com.financecash.application.dto.RecurringRuleCreateRequest;
import br.com.financecash.application.dto.RecurringRuleDTO;
import br.com.financecash.domain.model.*;
import br.com.financecash.domain.repository.AccountRepository;
import br.com.financecash.domain.repository.CategoryRepository;
import br.com.financecash.domain.repository.RecurringRuleRepository;
import br.com.financecash.exception.ResourceNotFoundException;
import br.com.financecash.security.CurrentUserProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * CRUD de regras recorrentes (salário, contas fixas, décimo terceiro). A geração
 * dos lançamentos futuros a partir da regra fica em {@link RecurringEntryGenerationService}.
 */
@Service
public class RecurringRuleService {

    private final RecurringRuleRepository recurringRuleRepository;
    private final CategoryRepository categoryRepository;
    private final AccountRepository accountRepository;
    private final CurrentUserProvider currentUserProvider;

    public RecurringRuleService(RecurringRuleRepository recurringRuleRepository, CategoryRepository categoryRepository,
                                 AccountRepository accountRepository, CurrentUserProvider currentUserProvider) {
        this.recurringRuleRepository = recurringRuleRepository;
        this.categoryRepository = categoryRepository;
        this.accountRepository = accountRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional(readOnly = true)
    public List<RecurringRuleDTO> listActive() {
        AppUser user = currentUserProvider.getCurrentUser();
        return recurringRuleRepository.findByOwnerIdAndActiveTrueOrderByNameAsc(user.getId())
                .stream().map(RecurringRuleDTO::from).toList();
    }

    @Transactional
    public RecurringRuleDTO create(RecurringRuleCreateRequest request) {
        AppUser user = currentUserProvider.getCurrentUser();

        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Categoria não encontrada: " + request.categoryId()));
        Account account = request.accountId() != null
                ? accountRepository.findById(request.accountId()).orElseThrow(() -> new ResourceNotFoundException("Conta não encontrada"))
                : null;

        RecurringRule rule = RecurringRule.builder()
                .owner(user)
                .name(request.name())
                .type(request.type())
                .category(category)
                .account(account)
                .frequency(request.frequency())
                .dayOfMonth(request.dayOfMonth())
                .referenceMonths(request.referenceMonths() != null ? new ArrayList<>(request.referenceMonths()) : new ArrayList<>())
                .startDate(request.startDate())
                .endDate(request.endDate())
                .active(true)
                .build();

        RecurringRuleValueHistory initialValue = RecurringRuleValueHistory.builder()
                .recurringRule(rule)
                .effectiveFrom(request.startDate())
                .amount(request.initialAmount())
                .build();
        rule.getValueHistory().add(initialValue);

        return RecurringRuleDTO.from(recurringRuleRepository.save(rule));
    }

    /**
     * Registra um novo valor vigente a partir de determinada data — usado para os
     * reajustes anuais/semestrais sem perder o histórico do valor anterior.
     */
    @Transactional
    public RecurringRuleDTO registerValueAdjustment(UUID ruleId, LocalDate effectiveFrom, BigDecimal newAmount) {
        RecurringRule rule = recurringRuleRepository.findById(ruleId)
                .orElseThrow(() -> new ResourceNotFoundException("Regra recorrente não encontrada: " + ruleId));

        rule.getValueHistory().add(RecurringRuleValueHistory.builder()
                .recurringRule(rule)
                .effectiveFrom(effectiveFrom)
                .amount(newAmount)
                .build());

        return RecurringRuleDTO.from(rule);
    }

    @Transactional
    public void deactivate(UUID ruleId) {
        RecurringRule rule = recurringRuleRepository.findById(ruleId)
                .orElseThrow(() -> new ResourceNotFoundException("Regra recorrente não encontrada: " + ruleId));
        rule.setActive(false);
    }
}
