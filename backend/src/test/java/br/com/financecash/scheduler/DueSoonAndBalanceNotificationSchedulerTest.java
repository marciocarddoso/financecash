package br.com.financecash.scheduler;

import br.com.financecash.application.dto.DashboardResponse;
import br.com.financecash.application.service.DashboardService;
import br.com.financecash.application.service.NotificationService;
import br.com.financecash.domain.model.AppUser;
import br.com.financecash.domain.model.Entry;
import br.com.financecash.domain.repository.AppUserRepository;
import br.com.financecash.domain.repository.EntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DueSoonAndBalanceNotificationSchedulerTest {

    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private EntryRepository entryRepository;
    @Mock
    private DashboardService dashboardService;
    @Mock
    private NotificationService notificationService;

    private DueSoonAndBalanceNotificationScheduler scheduler;
    private AppUser user;

    @BeforeEach
    void setUp() {
        scheduler = new DueSoonAndBalanceNotificationScheduler(
                appUserRepository, entryRepository, dashboardService, notificationService, 2);
        user = AppUser.builder().id(UUID.randomUUID()).email("marcio@example.com").active(true).build();
        when(appUserRepository.findByActiveTrue()).thenReturn(List.of(user));
    }

    private DashboardResponse dashboardWithProjection(BigDecimal projected) {
        return new DashboardResponse(LocalDate.now(), List.of(), List.of(),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, projected);
    }

    @Test
    void deveNotificarVencimentoQuandoHaLancamentosPendentesNoPrazo() {
        Entry entry = Entry.builder().id(UUID.randomUUID()).dueDate(LocalDate.now().plusDays(1)).build();
        when(entryRepository.findPendingBetween(eq(user.getId()), any(), any())).thenReturn(List.of(entry));
        when(dashboardService.getDashboardForUser(any(), any())).thenReturn(dashboardWithProjection(BigDecimal.TEN));

        scheduler.checkDueSoonAndBalanceForAllActiveUsers();

        verify(notificationService).notifyDueSoon(eq(user), anyList());
    }

    @Test
    void naoDeveNotificarVencimentoQuandoNaoHaLancamentosPendentes() {
        when(entryRepository.findPendingBetween(eq(user.getId()), any(), any())).thenReturn(List.of());
        when(dashboardService.getDashboardForUser(any(), any())).thenReturn(dashboardWithProjection(BigDecimal.TEN));

        scheduler.checkDueSoonAndBalanceForAllActiveUsers();

        verify(notificationService, never()).notifyDueSoon(any(), anyList());
    }

    @Test
    void deveNotificarSaldoNegativoQuandoProjecaoNegativa() {
        when(entryRepository.findPendingBetween(eq(user.getId()), any(), any())).thenReturn(List.of());
        when(dashboardService.getDashboardForUser(any(), any())).thenReturn(dashboardWithProjection(new BigDecimal("-50.00")));

        scheduler.checkDueSoonAndBalanceForAllActiveUsers();

        verify(notificationService).notifyNegativeBalance(eq(user), any(YearMonth.class), eq(new BigDecimal("-50.00")));
    }

    @Test
    void naoDeveNotificarSaldoNegativoQuandoProjecaoPositiva() {
        when(entryRepository.findPendingBetween(eq(user.getId()), any(), any())).thenReturn(List.of());
        when(dashboardService.getDashboardForUser(any(), any())).thenReturn(dashboardWithProjection(BigDecimal.TEN));

        scheduler.checkDueSoonAndBalanceForAllActiveUsers();

        verify(notificationService, never()).notifyNegativeBalance(any(), any(), any());
    }

    @Test
    void falhaEmUmUsuarioNaoInterrompeVerificacaoDosDemais() {
        AppUser other = AppUser.builder().id(UUID.randomUUID()).email("outro@example.com").active(true).build();
        when(appUserRepository.findByActiveTrue()).thenReturn(List.of(user, other));
        when(entryRepository.findPendingBetween(eq(user.getId()), any(), any())).thenThrow(new RuntimeException("boom"));
        when(entryRepository.findPendingBetween(eq(other.getId()), any(), any())).thenReturn(List.of());
        when(dashboardService.getDashboardForUser(eq(other), any())).thenReturn(dashboardWithProjection(BigDecimal.TEN));

        scheduler.checkDueSoonAndBalanceForAllActiveUsers();

        verify(dashboardService).getDashboardForUser(eq(other), any());
    }
}
