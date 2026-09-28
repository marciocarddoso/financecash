package br.com.financecash.application.service;

import br.com.financecash.application.dto.CreditCardDTO;
import br.com.financecash.application.dto.CreditCardUpdateRequest;
import br.com.financecash.domain.model.AppUser;
import br.com.financecash.domain.model.CreditCard;
import br.com.financecash.domain.repository.CreditCardRepository;
import br.com.financecash.exception.BusinessException;
import br.com.financecash.exception.ResourceNotFoundException;
import br.com.financecash.security.CurrentUserProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreditCardServiceTest {

    @Mock
    private CreditCardRepository creditCardRepository;
    @Mock
    private CurrentUserProvider currentUserProvider;

    private CreditCardService service() {
        return new CreditCardService(creditCardRepository, currentUserProvider);
    }

    private AppUser user() {
        return AppUser.builder().id(UUID.randomUUID()).email("marcio@example.com").name("Marcio").build();
    }

    @Test
    void deveDesativarCartaoDoProprioUsuario() {
        AppUser user = user();
        UUID cardId = UUID.randomUUID();
        CreditCard card = CreditCard.builder().id(cardId).owner(user).name("Nubank").bankName("Nubank")
                .closingDay(1).dueDay(10).active(true).build();
        when(currentUserProvider.getCurrentUser()).thenReturn(user);
        when(creditCardRepository.findById(cardId)).thenReturn(Optional.of(card));

        service().deactivate(cardId);

        assertThat(card.isActive()).isFalse();
    }

    @Test
    void deveRecusarDesativarCartaoDeOutroUsuario() {
        AppUser dono = user();
        AppUser outroUsuario = user();
        UUID cardId = UUID.randomUUID();
        CreditCard card = CreditCard.builder().id(cardId).owner(dono).name("Nubank").bankName("Nubank")
                .closingDay(1).dueDay(10).active(true).build();
        when(currentUserProvider.getCurrentUser()).thenReturn(outroUsuario);
        when(creditCardRepository.findById(cardId)).thenReturn(Optional.of(card));

        assertThatThrownBy(() -> service().deactivate(cardId))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void deveLancarNotFoundAoDesativarCartaoInexistente() {
        UUID cardId = UUID.randomUUID();
        when(creditCardRepository.findById(cardId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().deactivate(cardId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deveAtualizarNomeBancoDiaDeFechamentoEVencimento() {
        AppUser user = user();
        UUID cardId = UUID.randomUUID();
        CreditCard card = CreditCard.builder().id(cardId).owner(user).name("Banco Bradesco - Cartão")
                .bankName("Banco Bradesco").closingDay(31).dueDay(10).active(true).build();
        when(currentUserProvider.getCurrentUser()).thenReturn(user);
        when(creditCardRepository.findById(cardId)).thenReturn(Optional.of(card));
        when(creditCardRepository.save(any(CreditCard.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreditCardDTO dto = service().update(cardId, new CreditCardUpdateRequest("Bradesco Visa Infinite", "Banco Bradesco", 1, 10));

        assertThat(dto.name()).isEqualTo("Bradesco Visa Infinite");
        assertThat(dto.closingDay()).isEqualTo(1);
    }

    @Test
    void deveRecusarAtualizarCartaoDeOutroUsuario() {
        AppUser dono = user();
        AppUser outroUsuario = user();
        UUID cardId = UUID.randomUUID();
        CreditCard card = CreditCard.builder().id(cardId).owner(dono).name("Nubank").bankName("Nubank")
                .closingDay(1).dueDay(10).active(true).build();
        when(currentUserProvider.getCurrentUser()).thenReturn(outroUsuario);
        when(creditCardRepository.findById(cardId)).thenReturn(Optional.of(card));

        assertThatThrownBy(() -> service().update(cardId, new CreditCardUpdateRequest("Nubank", "Nubank", 1, 10)))
                .isInstanceOf(BusinessException.class);
    }
}
