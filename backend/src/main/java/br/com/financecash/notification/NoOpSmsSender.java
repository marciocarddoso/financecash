package br.com.financecash.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Implementação padrão de {@link SmsSender} — ver Javadoc da interface. */
@Component
public class NoOpSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(NoOpSmsSender.class);

    @Override
    public void send(String toPhoneNumber, String message) {
        log.info("SMS não enviado para {} — nenhum provedor de SMS configurado ainda (ver SmsSender). Mensagem: {}",
                toPhoneNumber, message);
    }
}
