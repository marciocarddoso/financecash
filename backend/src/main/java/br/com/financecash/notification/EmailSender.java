package br.com.financecash.notification;

/** Abstração sobre o envio de e-mail, para o NotificationService não depender direto do Spring Mail. */
public interface EmailSender {
    void send(String to, String subject, String body);
}
