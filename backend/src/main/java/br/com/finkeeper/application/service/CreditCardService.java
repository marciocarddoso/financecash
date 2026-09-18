package br.com.finkeeper.application.service;

import br.com.finkeeper.application.dto.CreditCardCreateRequest;
import br.com.finkeeper.application.dto.CreditCardDTO;
import br.com.finkeeper.domain.model.AppUser;
import br.com.finkeeper.domain.model.CreditCard;
import br.com.finkeeper.domain.repository.CreditCardRepository;
import br.com.finkeeper.security.CurrentUserProvider;
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
