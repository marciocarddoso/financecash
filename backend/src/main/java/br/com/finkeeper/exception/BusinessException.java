package br.com.finkeeper.exception;

/** Violação de uma regra de negócio (ex.: e-mail já cadastrado, parcelamento inválido). */
public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
}
