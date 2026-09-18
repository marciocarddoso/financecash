package br.com.financecash.scheduler;

import br.com.financecash.application.dto.DashboardResponse;
import br.com.financecash.application.service.DashboardService;
import br.com.financecash.application.service.NotificationService;
import br.com.financecash.domain.model.AppUser;
import br.com.financecash.domain.model.Entry;
import br.com.financecash.domain.repository.AppUserRepository;
import br.com.financecash.domain.repository.EntryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * Fase 3 do roadmap: "verifica contas vencendo nas próximas 24-48h e dispara
 * notificação" + "verifica projeção de saldo negativo no mês e alerta". Roda depois
 * do RecurringEntryScheduler (que gera os lançamentos do mês antes de qualquer
 * verificação fazer sentido) — ver o cron em application.yml.
 *
 * Cada usuário controla se quer esses alertas em NotificationPreferencesDTO
 * (AppUser.notifyDueSoonEmail / notifyNegativeBalanceEmail); quem decide o que
 * realmente é enviado é o NotificationService.
 */
@Component
public class DueSoonAndBalanceNotificationScheduler {

    private static final Logger log = LoggerFactory.getLogger(DueSoonAndBalanceNotificationScheduler.class);

    private final AppUserRepository appUserRepository;
    private final EntryRepository entryRepository;
    private final DashboardService dashboardService;
    private final NotificationService notificationService;
    private final int dueSoonDays;

    public DueSoonAndBalanceNotificationScheduler(
            AppUserRepository appUserRepository,
            EntryRepository entryRepository,
            DashboardService dashboardService,
            NotificationService notificationService,
            @Value("${financecash.notifications.due-soon-days:2}") int dueSoonDays) {
        this.appUserRepository = appUserRepository;
        this.entryRepository = entryRepository;
        this.dashboardService = dashboardService;
        this.notificationService = notificationService;
        this.dueSoonDays = dueSoonDays;
    }

    /**
     * Roda todo dia às 06:00 (horário do servidor), uma hora depois da geração de
     * recorrências. Cron configurável via `financecash.notifications.cron`.
     */
    @Scheduled(cron = "${financecash.notifications.cron:0 0 6 * * *}")
    @Transactional(readOnly = true)
    public void checkDueSoonAndBalanceForAllActiveUsers() {
        List<AppUser> activeUsers = appUserRepository.findByActiveTrue();
        LocalDate today = LocalDate.now();
        LocalDate dueSoonLimit = today.plusDays(dueSoonDays);

        log.info("Verificando contas a vencer (até {}) e projeção de saldo para {} usuário(s).",
                dueSoonLimit, activeUsers.size());

        for (AppUser user : activeUsers) {
            try {
                checkDueSoon(user, today, dueSoonLimit);
                checkNegativeBalance(user, today);
            } catch (Exception ex) {
                // Mesma lógica do RecurringEntryScheduler: falha em um usuário não pode
                // travar a checagem dos demais.
                log.error("Falha ao verificar notificações para o usuário {}: {}", user.getEmail(), ex.getMessage(), ex);
            }
        }
    }

    private void checkDueSoon(AppUser user, LocalDate today, LocalDate dueSoonLimit) {
        List<Entry> dueSoon = entryRepository.findPendingBetween(user.getId(), today, dueSoonLimit);
        if (!dueSoon.isEmpty()) {
            notificationService.notifyDueSoon(user, dueSoon);
        }
    }

    private void checkNegativeBalance(AppUser user, LocalDate today) {
        DashboardResponse dashboard = dashboardService.getDashboardForUser(user, today);
        BigDecimal projected = dashboard.projectedBalanceEndOfMonth();
        if (projected.compareTo(BigDecimal.ZERO) < 0) {
            notificationService.notifyNegativeBalance(user, YearMonth.from(today), projected);
        }
    }
}
