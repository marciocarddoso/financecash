package br.com.financecash.scheduler;

import br.com.financecash.application.service.RecurringEntryGenerationService;
import br.com.financecash.domain.model.AppUser;
import br.com.financecash.domain.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecurringEntrySchedulerTest {

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private RecurringEntryGenerationService generationService;

    private RecurringEntryScheduler scheduler;

    private AppUser userA;
    private AppUser userB;

    @BeforeEach
    void setUp() {
        scheduler = new RecurringEntryScheduler(appUserRepository, generationService, 12);
        userA = AppUser.builder().id(UUID.randomUUID()).email("a@example.com").name("A").passwordHash("x").active(true).build();
        userB = AppUser.builder().id(UUID.randomUUID()).email("b@example.com").name("B").passwordHash("x").active(true).build();
    }

    @Test
    void deveGerarLancamentosParaCadaUsuarioAtivo() {
        when(appUserRepository.findByActiveTrue()).thenReturn(List.of(userA, userB));
        when(generationService.generateForUser(any(), any(), eq(12))).thenReturn(2);

        scheduler.generateForAllActiveUsers();

        verify(generationService).generateForUser(eq(userA), any(LocalDate.class), eq(12));
        verify(generationService).generateForUser(eq(userB), any(LocalDate.class), eq(12));
    }

    @Test
    void falhaEmUmUsuarioNaoDeveInterromperOsDemais() {
        when(appUserRepository.findByActiveTrue()).thenReturn(List.of(userA, userB));
        when(generationService.generateForUser(eq(userA), any(), eq(12))).thenThrow(new RuntimeException("boom"));
        when(generationService.generateForUser(eq(userB), any(), eq(12))).thenReturn(1);

        scheduler.generateForAllActiveUsers();

        verify(generationService).generateForUser(eq(userA), any(LocalDate.class), eq(12));
        verify(generationService).generateForUser(eq(userB), any(LocalDate.class), eq(12));
    }

    @Test
    void naoDeveChamarGeracaoQuandoNaoHaUsuariosAtivos() {
        when(appUserRepository.findByActiveTrue()).thenReturn(List.of());

        scheduler.generateForAllActiveUsers();

        verifyNoInteractions(generationService);
    }
}
