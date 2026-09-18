package br.com.financecash.application.service;

import br.com.financecash.domain.model.AppUser;
import br.com.financecash.domain.model.Category;
import br.com.financecash.domain.model.Entry;
import br.com.financecash.domain.model.EntryStatus;
import br.com.financecash.domain.model.EntryType;
import br.com.financecash.notification.EmailSender;
import br.com.financecash.notification.SmsSender;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private EmailSender emailSender;
    @Mock
    private SmsSender smsSender;

    private NotificationService notificationService;

    private NotificationService service() {
        return new NotificationService(emailSender, smsSender);
    }

    private AppUser user(boolean dueSoonEmail, boolean negativeBalanceEmail, boolean sms, String phone) {
        return AppUser.builder()
                .id(UUID.randomUUID())
                .email("marcio@example.com")
                .name("Marcio")
                .notifyDueSoonEmail(dueSoonEmail)
                .notifyNegativeBalanceEmail(negativeBalanceEmail)
                .notifySmsEnabled(sms)
                .phoneNumber(phone)
                .build();
    }

    private Entry entry() {
        Category category = Category.builder().id(UUID.randomUUID()).name("Cartão").build();
        return Entry.builder()
                .id(UUID.randomUUID())
                .description("Fatura Bradesco")
                .amount(new BigDecimal("150.00"))
                .dueDate(LocalDate.now().plusDays(1))
                .type(EntryType.DESPESA)
                .status(EntryStatus.PENDENTE)
                .category(category)
                .build();
    }

    @Test
    void deveEnviarEmailDeVencimentoQuandoPreferenciaHabilitada() {
        notificationService = service();
        AppUser u = user(true, true, false, null);

        notificationService.notifyDueSoon(u, List.of(entry()));

        verify(emailSender).send(eq("marcio@example.com"), any(), any());
        verify(smsSender, never()).send(any(), any());
    }

    @Test
    void naoDeveEnviarEmailDeVencimentoQuandoPreferenciaDesligada() {
        notificationService = service();
        AppUser u = user(false, true, false, null);

        notificationService.notifyDueSoon(u, List.of(entry()));

        verify(emailSender, never()).send(any(), any(), any());
    }

    @Test
    void naoDeveEnviarNadaQuandoListaDeVencimentosVazia() {
        notificationService = service();
        AppUser u = user(true, true, false, null);

        notificationService.notifyDueSoon(u, List.of());

        verify(emailSender, never()).send(any(), any(), any());
    }

    @Test
    void deveEnviarSmsQuandoHabilitadoETelefonePresente() {
        notificationService = service();
        AppUser u = user(true, true, true, "+5511999999999");

        notificationService.notifyDueSoon(u, List.of(entry()));

        verify(smsSender).send(eq("+5511999999999"), any());
    }

    @Test
    void naoDeveEnviarSmsQuandoTelefoneAusenteMesmoComPreferenciaLigada() {
        notificationService = service();
        AppUser u = user(true, true, true, null);

        notificationService.notifyDueSoon(u, List.of(entry()));

        verify(smsSender, never()).send(any(), any());
    }

    @Test
    void deveEnviarEmailDeSaldoNegativoQuandoPreferenciaHabilitada() {
        notificationService = service();
        AppUser u = user(true, true, false, null);

        notificationService.notifyNegativeBalance(u, YearMonth.now(), new BigDecimal("-320.50"));

        verify(emailSender).send(eq("marcio@example.com"), any(), any());
    }

    @Test
    void naoDeveEnviarEmailDeSaldoNegativoQuandoPreferenciaDesligada() {
        notificationService = service();
        AppUser u = user(true, false, false, null);

        notificationService.notifyNegativeBalance(u, YearMonth.now(), new BigDecimal("-1.00"));

        verify(emailSender, never()).send(any(), any(), any());
    }
}
