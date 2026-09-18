package br.com.financecash.notification;

/**
 * Abstração sobre o envio de SMS. Existe desde já para o usuário poder ligar a
 * preferência de SMS (AppUser.notifySmsEnabled) sem exigir migração de schema depois
 * — mas nesta versão não há nenhum provedor de SMS integrado (exigiria conta paga em
 * um serviço como Twilio ou Zenvia). A implementação padrão ({@link NoOpSmsSender})
 * apenas registra em log que o envio foi pedido e não foi feito. Ver
 * docs/ROADMAP.md, Fase 3.
 */
public interface SmsSender {
    void send(String toPhoneNumber, String message);
}
