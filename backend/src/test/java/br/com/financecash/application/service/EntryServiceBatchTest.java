package br.com.financecash.application.service;

import br.com.financecash.application.dto.EntryBatchOperationResult;
import br.com.financecash.domain.model.AppUser;
import br.com.financecash.domain.model.Entry;
import br.com.financecash.domain.model.EntryStatus;
import br.com.financecash.domain.repository.AccountRepository;
import br.com.financecash.domain.repository.CategoryRepository;
import br.com.financecash.domain.repository.CreditCardRepository;
import br.com.financecash.domain.repository.EntryRepository;
import br.com.financecash.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntryServiceBatchTest {

    @Mock
    private EntryRepository entryRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private CreditCardRepository creditCardRepository;
    @Mock
    private CurrentUserProvider currentUserProvider;

    private EntryService service;
    private AppUser user;
    private AppUser otherUser;

    @BeforeEach
    void setUp() {
        service = new EntryService(entryRepository, categoryRepository, accountRepository, creditCardRepository, currentUserProvider);
        user = AppUser.builder().id(UUID.randomUUID()).build();
        otherUser = AppUser.builder().id(UUID.randomUUID()).build();
        when(currentUserProvider.getCurrentUser()).thenReturn(user);
    }

    private Entry entryOf(AppUser owner) {
        return Entry.builder().id(UUID.randomUUID()).owner(owner).status(EntryStatus.PENDENTE).build();
    }

    @Test
    void deveMarcarComoPagoApenasOsLancamentosDoUsuarioLogado() {
        Entry mine = entryOf(user);
        Entry someoneElses = entryOf(otherUser);
        UUID missingId = UUID.randomUUID();

        when(entryRepository.findById(mine.getId())).thenReturn(Optional.of(mine));
        when(entryRepository.findById(someoneElses.getId())).thenReturn(Optional.of(someoneElses));
        when(entryRepository.findById(missingId)).thenReturn(Optional.empty());

        EntryBatchOperationResult result = service.batchMarkAsPaid(
                List.of(mine.getId(), someoneElses.getId(), missingId), LocalDate.of(2026, 10, 1));

        assertThat(result.affected()).isEqualTo(1);
        assertThat(result.notFound()).containsExactlyInAnyOrder(someoneElses.getId(), missingId);
        assertThat(mine.getStatus()).isEqualTo(EntryStatus.PAGO);
        assertThat(someoneElses.getStatus()).isEqualTo(EntryStatus.PENDENTE);
    }

    @Test
    void deveUsarDataDeHojeQuandoPaymentDateNulo() {
        Entry mine = entryOf(user);
        when(entryRepository.findById(mine.getId())).thenReturn(Optional.of(mine));

        service.batchMarkAsPaid(List.of(mine.getId()), null);

        assertThat(mine.getPaymentDate()).isEqualTo(LocalDate.now());
    }

    @Test
    void deveExcluirApenasOsLancamentosDoUsuarioLogado() {
        Entry mine = entryOf(user);
        Entry someoneElses = entryOf(otherUser);

        when(entryRepository.findById(mine.getId())).thenReturn(Optional.of(mine));
        when(entryRepository.findById(someoneElses.getId())).thenReturn(Optional.of(someoneElses));

        EntryBatchOperationResult result = service.batchDelete(List.of(mine.getId(), someoneElses.getId()));

        assertThat(result.affected()).isEqualTo(1);
        assertThat(result.notFound()).containsExactly(someoneElses.getId());
        verify(entryRepository).delete(mine);
        verify(entryRepository, never()).delete(someoneElses);
    }
}
