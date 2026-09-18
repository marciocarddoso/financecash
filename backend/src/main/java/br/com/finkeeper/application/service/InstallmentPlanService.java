package br.com.finkeeper.application.service;

import br.com.finkeeper.application.dto.InstallmentPlanCreateRequest;
import br.com.finkeeper.application.dto.InstallmentPlanDTO;
import br.com.finkeeper.domain.model.*;
import br.com.finkeeper.domain.repository.*;
import br.com.finkeeper.exception.ResourceNotFoundException;
import br.com.finkeeper.security.CurrentUserProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Ao criar um InstallmentPlan (compra parcelada), gera de uma vez todos os Entry
 * das parcelas futuras — é o "provisionamento de parcelamento" citado no pedido
 * original: ao lançar a compra, todas as parcelas já ficam visíveis nos meses seguintes.
 */
@Service
public class InstallmentPlanService {

    private final InstallmentPlanRepository installmentPlanRepository;
    private final EntryRepository entryRepository;
    private final CategoryRepository categoryRepository;
    private final AccountRepository accountRepository;
    private final CreditCardRepository creditCardRepository;
    private final CurrentUserProvider currentUserProvider;

    public InstallmentPlanService(InstallmentPlanRepository installmentPlanRepository, EntryRepository entryRepository,
                                   CategoryRepository categoryRepository, AccountRepository accountRepository,
                                   CreditCardRepository creditCardRepository, CurrentUserProvider currentUserProvider) {
        this.installmentPlanRepository = installmentPlanRepository;
        this.entryRepository = entryRepository;
        this.categoryRepository = categoryRepository;
        this.accountRepository = accountRepository;
        this.creditCardRepository = creditCardRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional
    public InstallmentPlanDTO create(InstallmentPlanCreateRequest request) {
        AppUser user = currentUserProvider.getCurrentUser();

        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Categoria não encontrada: " + request.categoryId()));
        Account account = request.accountId() != null
                ? accountRepository.findById(request.accountId()).orElseThrow(() -> new ResourceNotFoundException("Conta não encontrada"))
                : null;
        CreditCard creditCard = request.creditCardId() != null
                ? creditCardRepository.findById(request.creditCardId()).orElseThrow(() -> new ResourceNotFoundException("Cartão não encontrado"))
                : null;

        InstallmentPlan plan = InstallmentPlan.builder()
                .owner(user)
                .description(request.description())
                .totalAmount(request.totalAmount())
                .installmentsCount(request.installmentsCount())
                .firstDueDate(request.firstDueDate())
                .category(category)
                .account(account)
                .creditCard(creditCard)
                .build();
        InstallmentPlan saved = installmentPlanRepository.save(plan);

        generateInstallmentEntries(user, saved);

        return InstallmentPlanDTO.from(saved);
    }

    private void generateInstallmentEntries(AppUser user, InstallmentPlan plan) {
        BigDecimal baseInstallment = plan.getTotalAmount()
                .divide(BigDecimal.valueOf(plan.getInstallmentsCount()), 2, RoundingMode.DOWN);
        BigDecimal accumulated = baseInstallment.multiply(BigDecimal.valueOf(plan.getInstallmentsCount() - 1));
        BigDecimal lastInstallment = plan.getTotalAmount().subtract(accumulated); // absorve o arredondamento na última parcela

        for (int i = 1; i <= plan.getInstallmentsCount(); i++) {
            BigDecimal amount = (i == plan.getInstallmentsCount()) ? lastInstallment : baseInstallment;

            Entry entry = Entry.builder()
                    .owner(user)
                    .description("%s (%d/%d)".formatted(plan.getDescription(), i, plan.getInstallmentsCount()))
                    .amount(amount)
                    .dueDate(plan.getFirstDueDate().plusMonths(i - 1))
                    .type(EntryType.DESPESA)
                    .status(EntryStatus.PENDENTE)
                    .origin(EntryOrigin.PARCELAMENTO)
                    .category(plan.getCategory())
                    .account(plan.getAccount())
                    .creditCard(plan.getCreditCard())
                    .installmentPlan(plan)
                    .installmentNumber(i)
                    .build();

            entryRepository.save(entry);
        }
    }
}
