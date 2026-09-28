package br.com.financecash.application.service;

import br.com.financecash.application.dto.CreditCardCreateRequest;
import br.com.financecash.application.dto.CreditCardDTO;
import br.com.financecash.application.dto.CreditCardUpdateRequest;
import br.com.financecash.domain.model.AppUser;
import br.com.financecash.domain.model.CreditCard;
import br.com.financecash.domain.repository.CreditCardRepository;
import br.com.financecash.exception.BusinessException;
import br.com.financecash.exception.ResourceNotFoundException;
import br.com.financecash.security.CurrentUserProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class CreditCardService {

    private final CreditCardRepository creditCardRepository;
    private final CurrentUserProvider currentUserProvider;

    public CreditCardService(CreditCardRepository creditCardRepository, CurrentUserProvider currentUserProvider) {
        this.creditCardRepository = creditCardRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional(readOnly = true)
    public List<CreditCardDTO> listActive() {
        AppUser user = currentUserProvider.getCurrentUser();
        return creditCardRepository.findByOwnerIdAndActiveTrueOrderByNameAsc(user.getId())
                .stream().map(CreditCardDTO::from).toList();
    }

    @Transactional
    public CreditCardDTO create(CreditCardCreateRequest request) {
        AppUser user = currentUserProvider.getCurrentUser();
        CreditCard card = CreditCard.builder()
                .owner(user)
                .name(request.name())
                .bankName(request.bankName())
                .closingDay(request.closingDay())
                .dueDay(request.dueDay())
                .active(true)
                .build();
        return CreditCardDTO.from(creditCardRepository.save(card));
    }

    /** Corrige nome/banco/dia de fechamento/dia de vencimento de um cartão já cadastrado — ex.: ajustar o dia de fechamento estimado pelo AccountSyncService (a Pluggy nem sempre informa esse dado). */
    @Transactional
    public CreditCardDTO update(UUID creditCardId, CreditCardUpdateRequest request) {
        AppUser user = currentUserProvider.getCurrentUser();
        CreditCard card = creditCardRepository.findById(creditCardId)
                .orElseThrow(() -> new ResourceNotFoundException("Cartão não encontrado: " + creditCardId));
        if (!card.getOwner().getId().equals(user.getId())) {
            throw new BusinessException("Este cartão não pertence ao usuário logado.");
        }
        card.setName(request.name());
        card.setBankName(request.bankName());
        card.setClosingDay(request.closingDay());
        card.setDueDay(request.dueDay());
        return CreditCardDTO.from(creditCardRepository.save(card));
    }

    /** Desativa (soft delete) um cartão — ex.: um cadastro manual que ficou obsoleto depois de sincronizar via Open Finance. */
    @Transactional
    public void deactivate(UUID creditCardId) {
        AppUser user = currentUserProvider.getCurrentUser();
        CreditCard card = creditCardRepository.findById(creditCardId)
                .orElseThrow(() -> new ResourceNotFoundException("Cartão não encontrado: " + creditCardId));
        if (!card.getOwner().getId().equals(user.getId())) {
            throw new BusinessException("Este cartão não pertence ao usuário logado.");
        }
        card.setActive(false);
        creditCardRepository.save(card);
    }
}
