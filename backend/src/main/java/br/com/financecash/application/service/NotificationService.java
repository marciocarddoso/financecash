package br.com.financecash.application.service;

import br.com.financecash.domain.model.AppUser;
import br.com.financecash.domain.model.Entry;
import br.com.financecash.notification.EmailSender;
import br.com.financecash.notification.SmsSender;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Orquestra os alertas da Fase 3 do roadmap: conta vencendo em breve e projeção de
 * saldo negativo no mês. Cada usuário decide, em NotificationPreferencesDTO, quais
 * alertas quer receber e por qual canal — ver AppUser.notifyDueSoonEmail /
 * notifyNegativeBalanceEmail / notifySmsEnabled.
 *
 * Quem dispara isso é o DueSoonAndBalanceNotificationScheduler, dentro do job diário.
 */
@Service
public class NotificationService {

    private final EmailSender emailSender;
    private final SmsSender smsSender;

    public NotificationService(EmailSender emailSender, SmsSender smsSender) {
        this.emailSender = emailSender;
        this.smsSender = smsSender;
    }

    public void notifyDueSoon(AppUser user, List<Entry> dueSoonEntries) {
        if (dueSoonEntries.isEmpty()) {
            return;
        }

        String subject = dueSoonEntries.size() == 1
                ? "FinanceCash: 1 conta vencendo em breve"
                : "FinanceCash: " + dueSoonEntries.size() + " contas vencendo em breve";

        String body = "Contas com vencimento nos próximos dias:\n\n"
                + dueSoonEntries.stream()
                        .map(e -> "- " + e.getDueDate() + " | " + e.getDescription() + " | R$ " + e.getAmount())
                        .collect(Collectors.joining("\n"))
                + "\n\nAcesse o FinanceCash para conferir ou marcar como pago.";

        send(user, subject, body);
    }

    public void notifyNegativeBalance(AppUser user, YearMonth month, BigDecimal projectedBalance) {
        String subject = "FinanceCash: projeção de saldo negativo em " + month;
        String body = "A projeção de saldo para o fim de " + month + " está negativa: R$ " + projectedBalance
                + "\n\nAcesse o FinanceCash para revisar os lançamentos pendentes do mês.";

        sendIfEnabled(user, user.isNotifyNegativeBalanceEmail(), subject, body);
    }

    private void send(AppUser user, String subject, String body) {
        sendIfEnabled(user, user.isNotifyDueSoonEmail(), subject, body);
    }

    private void sendIfEnabled(AppUser user, boolean emailEnabled, String subject, String body) {
        if (emailEnabled) {
            emailSender.send(user.getEmail(), subject, body);
        }
        if (user.isNotifySmsEnabled() && user.getPhoneNumber() != null && !user.getPhoneNumber().isBlank()) {
            smsSender.send(user.getPhoneNumber(), subject);
        }
    }
}
