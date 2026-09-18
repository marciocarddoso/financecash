package br.com.financecash.application.service;

import br.com.financecash.application.dto.EntryBatchOperationResult;
import br.com.financecash.application.dto.EntryCreateRequest;
import br.com.financecash.application.dto.EntryDTO;
import br.com.financecash.domain.model.*;
import br.com.financecash.domain.repository.AccountRepository;
import br.com.financecash.domain.repository.CategoryRepository;
import br.com.financecash.domain.repository.CreditCardRepository;
import br.com.financecash.domain.repository.EntryRepository;
import br.com.financecash.exception.ResourceNotFoundException;
import br.com.financecash.security.CurrentUserProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class EntryService {

    private final EntryRepository entryRepository;
    private final CategoryRepository categoryRepository;
    private final AccountRepository accountRepository;
    private final CreditCardRepository creditCardRepository;
    private final CurrentUserProvider currentUserProvider;

    public EntryService(EntryRepository entryRepository, CategoryRepository categoryRepository,
                         AccountRepository accountRepository, CreditCardRepository creditCardRepository,
                         CurrentUserProvider currentUserProvider) {
        this.entryRepository = entryRepository;
        this.categoryRepository = categoryRepository;
        this.accountRepository = accountRepository;
        this.creditCardRepository = creditCardRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional(readOnly = true)
    public List<EntryDTO> listBetween(LocalDate from, LocalDate to) {
        AppUser user = currentUserProvider.getCurrentUser();
        return entryRepository.findByOwnerIdAndDueDateBetweenOrderByDueDateAsc(user.getId(), from, to)
                .stream().map(EntryDTO::from).toList();
    }

    @Transactional
    public EntryDTO create(EntryCreateRequest request) {
        AppUser user = currentUserProvider.getCurrentUser();

        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Categoria não encontrada: " + request.categoryId()));

        Account account = request.accountId() != null
                ? accountRepository.findById(request.accountId())
                    .orElseThrow(() -> new ResourceNotFoundException("Conta não encontrada: " + request.accountId()))
                : null;

        CreditCard creditCard = request.creditCardId() != null
                ? creditCardRepository.findById(request.creditCardId())
                    .orElseThrow(() -> new ResourceNotFoundException("Cartão não encontrado: " + request.creditCardId()))
                : null;

        Entry entry = Entry.builder()
                .owner(user)
                .description(request.description())
                .amount(request.amount())
                .dueDate(request.dueDate())
                .type(request.type())
                .status(EntryStatus.PENDENTE)
                .origin(EntryOrigin.MANUAL)
                .category(category)
                .account(account)
                .creditCard(creditCard)
                .build();

        return EntryDTO.from(entryRepository.save(entry));
    }

    @Transactional
    public EntryDTO markAsPaid(UUID entryId, LocalDate paymentDate) {
        Entry entry = entryRepository.findById(entryId)
                .orElseThrow(() -> new ResourceNotFoundException("Lançamento não encontrado: " + entryId));
        entry.markAsPaid(paymentDate != null ? paymentDate : LocalDate.now());
        return EntryDTO.from(entry);
    }

    @Transactional
    public void delete(UUID entryId) {
        if (!entryRepository.existsById(entryId)) {
            throw new ResourceNotFoundException("Lançamento não encontrado: " + entryId);
        }
        entryRepository.deleteById(entryId);
    }

    /**
     * Marca vários lançamentos como pagos de uma vez (Fase 1 do roadmap: "edição em
     * lote"). ids que não existem ou não pertencem ao usuário logado entram em
     * notFound() em vez de derrubar a operação inteira — ver Javadoc de
     * EntryBatchOperationResult.
     */
    @Transactional
    public EntryBatchOperationResult batchMarkAsPaid(List<UUID> ids, LocalDate paymentDate) {
        AppUser user = currentUserProvider.getCurrentUser();
        LocalDate effectiveDate = paymentDate != null ? paymentDate : LocalDate.now();

        List<UUID> notFound = new ArrayList<>();
        int affected = 0;
        for (UUID id : ids) {
            Entry entry = entryRepository.findById(id).orElse(null);
            if (entry == null || !entry.getOwner().getId().equals(user.getId())) {
                notFound.add(id);
                continue;
            }
            entry.markAsPaid(effectiveDate);
            affected++;
        }
        return new EntryBatchOperationResult(affected, notFound);
    }

    /** Exclui vários lançamentos de uma vez — mesma semântica de notFound() acima. */
    @Transactional
    public EntryBatchOperationResult batchDelete(List<UUID> ids) {
        AppUser user = currentUserProvider.getCurrentUser();

        List<UUID> notFound = new ArrayList<>();
        int affected = 0;
        for (UUID id : ids) {
            Entry entry = entryRepository.findById(id).orElse(null);
            if (entry == null || !entry.getOwner().getId().equals(user.getId())) {
                notFound.add(id);
                continue;
            }
            entryRepository.delete(entry);
            affected++;
        }
        return new EntryBatchOperationResult(affected, notFound);
    }
}
