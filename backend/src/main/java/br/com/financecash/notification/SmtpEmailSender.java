package br.com.financecash.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Envio de e-mail via SMTP (Spring Mail). Configuração em application.yml, via as
 * variáveis de ambiente MAIL_HOST/MAIL_PORT/MAIL_USERNAME/MAIL_PASSWORD/MAIL_FROM —
 * sem isso configurado, o host fica vazio e o envio falha, mas é capturado aqui e
 * apenas logado: uma falha de e-mail nunca pode derrubar o job agendado que gera os
 * lançamentos recorrentes (ver DueSoonAndBalanceNotificationScheduler).
 */
@Component
public class SmtpEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);

    private final JavaMailSender mailSender;
    private final String from;

    public SmtpEmailSender(JavaMailSender mailSender,
                            @Value("${financecash.notifications.email-from:no-reply@financecash.local}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void send(String to, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            log.info("E-mail de notificação enviado para {}: {}", to, subject);
        } catch (Exception ex) {
            log.warn("Não foi possível enviar e-mail de notificação para {} (SMTP configurado? ver "
                    + "financecash.notifications.email-from e spring.mail.* em application.yml): {}",
                    to, ex.getMessage());
        }
    }
}
