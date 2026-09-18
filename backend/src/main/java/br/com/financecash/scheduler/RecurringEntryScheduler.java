package br.com.financecash.scheduler;

import br.com.financecash.application.service.RecurringEntryGenerationService;
import br.com.financecash.domain.model.AppUser;
import br.com.financecash.domain.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Fase 3 do roadmap: em vez de depender de chamar POST /api/recurring-rules/generate
 * manualmente, este job roda todo dia e garante que os lançamentos recorrentes dos
 * próximos meses (salário, contas fixas, décimo terceiro etc.) já existam para todos
 * os usuários ativos — é o "provisionamento automático" citado no pedido original.
 *
 * Não depende de SecurityContext (não há requisição HTTP disparando o job), por isso
 * usa {@link RecurringEntryGenerationService#generateForUser} diretamente com o
 * AppUser carregado do repositório, em vez de CurrentUserProvider (que só funciona
 * dentro de uma requisição autenticada).
 */
@Component
public class RecurringEntryScheduler {

    private static final Logger log = LoggerFactory.getLogger(RecurringEntryScheduler.class);

    private final AppUserRepository appUserRepository;
    private final RecurringEntryGenerationService generationService;
    private final int monthsAhead;

    public RecurringEntryScheduler(
            AppUserRepository appUserRepository,
            RecurringEntryGenerationService generationService,
            @Value("${financecash.recurring-generation.months-ahead:12}") int monthsAhead) {
        this.appUserRepository = appUserRepository;
        this.generationService = generationService;
        this.monthsAhead = monthsAhead;
    }

    /**
     * Roda todo dia às 05:00 (horário do servidor). Cron configurável via
     * `financecash.recurring-generation.cron` — ver application.yml.
     */
    @Scheduled(cron = "${financecash.recurring-generation.cron:0 0 5 * * *}")
    public void generateForAllActiveUsers() {
        List<AppUser> activeUsers = appUserRepository.findByActiveTrue();
        LocalDate today = LocalDate.now();

        log.info("Iniciando geração automática de lançamentos recorrentes para {} usuário(s), {} meses à frente.",
                activeUsers.size(), monthsAhead);

        int totalCreated = 0;
        for (AppUser user : activeUsers) {
            try {
                int created = generationService.generateForUser(user, today, monthsAhead);
                totalCreated += created;
                if (created > 0) {
                    log.info("Usuário {}: {} lançamento(s) recorrente(s) gerado(s).", user.getEmail(), created);
                }
            } catch (Exception ex) {
                // Uma falha em um usuário não pode travar a geração dos demais.
                log.error("Falha ao gerar lançamentos recorrentes para o usuário {}: {}", user.getEmail(), ex.getMessage(), ex);
            }
        }

        log.info("Geração automática concluída. Total de lançamentos criados: {}.", totalCreated);
    }
}
