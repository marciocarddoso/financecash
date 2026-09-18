package br.com.financecash.application.service;

import br.com.financecash.application.dto.CreditCardCreateRequest;
import br.com.financecash.application.dto.CreditCardDTO;
import br.com.financecash.domain.model.AppUser;
import br.com.financecash.domain.model.CreditCard;
import br.com.financecash.domain.repository.CreditCardRepository;
import br.com.financecash.security.CurrentUserProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
}
