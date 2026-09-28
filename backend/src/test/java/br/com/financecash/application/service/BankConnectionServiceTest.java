package br.com.financecash.application.service;

import br.com.financecash.application.dto.BankConnectionCreateRequest;
import br.com.financecash.application.dto.BankConnectionDTO;
import br.com.financecash.domain.model.AppUser;
import br.com.financecash.domain.model.BankConnection;
import br.com.financecash.domain.model.BankConnectionStatus;
import br.com.financecash.domain.repository.BankConnectionRepository;
import br.com.financecash.exception.BusinessException;
import br.com.financecash.openfinance.PluggyClient;
import br.com.financecash.security.CurrentUserProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BankConnectionServiceTest {

    @Mock
    private BankConnectionRepository bankConnectionRepository;
    @Mock
    private PluggyClient pluggyClient;
    @Mock
    private CurrentUserProvider currentUserProvider;

    private BankConnectionService service() {
        return new BankConnectionService(bankConnectionRepository, pluggyClient, currentUserProvider);
    }

    private AppUser user() {
        return AppUser.builder().id(UUID.randomUUID()).email("marcio@example.com").name("Marcio").build();
    }

    @Test
    void deveSalvarConexaoBuscandoNomeDoBancoNaPluggy() {
        AppUser user = user();
        when(currentUserProvider.getCurrentUser()).thenReturn(user);
        when(bankConnectionRepository.existsByOwnerIdAndItemId(user.getId(), "item-1")).thenReturn(false);
        when(pluggyClient.getItem("item-1")).thenReturn(new PluggyClient.ItemInfo("item-1", "Nubank", "UPDATED"));
        when(bankConnectionRepository.save(any(BankConnection.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BankConnectionDTO dto = service().save(new BankConnectionCreateRequest("item-1"));

        assertThat(dto.bankName()).isEqualTo("Nubank");
        assertThat(dto.status()).isEqualTo(BankConnectionStatus.ATIVA);
    }

    @Test
    void deveMapearStatusOutdatedComoExpirada() {
        AppUser user = user();
        when(currentUserProvider.getCurrentUser()).thenReturn(user);
        when(bankConnectionRepository.existsByOwnerIdAndItemId(user.getId(), "item-1")).thenReturn(false);
        when(pluggyClient.getItem("item-1")).thenReturn(new PluggyClient.ItemInfo("item-1", "Bradesco", "OUTDATED"));
        when(bankConnectionRepository.save(any(BankConnection.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BankConnectionDTO dto = service().save(new BankConnectionCreateRequest("item-1"));

        assertThat(dto.status()).isEqualTo(BankConnectionStatus.EXPIRADA);
    }

    @Test
    void deveRecusarConexaoJaExistente() {
        AppUser user = user();
        when(currentUserProvider.getCurrentUser()).thenReturn(user);
        when(bankConnectionRepository.existsByOwnerIdAndItemId(user.getId(), "item-1")).thenReturn(true);

        assertThatThrownBy(() -> service().save(new BankConnectionCreateRequest("item-1")))
                .isInstanceOf(BusinessException.class);

        verify(pluggyClient, never()).getItem(any());
    }

    @Test
    void deveListarConexoesDoUsuarioAtual() {
        AppUser user = user();
        when(currentUserProvider.getCurrentUser()).thenReturn(user);
        BankConnection connection = BankConnection.builder()
                .id(UUID.randomUUID())
                .owner(user)
                .itemId("item-1")
                .bankName("C6 Bank")
                .status(BankConnectionStatus.ATIVA)
                .build();
        when(bankConnectionRepository.findByOwnerIdOrderByConnectedAtDesc(user.getId())).thenReturn(List.of(connection));

        List<BankConnectionDTO> result = service().list();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).bankName()).isEqualTo("C6 Bank");
    }
}
