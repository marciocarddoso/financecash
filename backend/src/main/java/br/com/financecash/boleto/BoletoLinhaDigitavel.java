package br.com.financecash.boleto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Resultado da leitura de uma linha digitável de boleto bancário (47 dígitos).
 *
 * @param valid          true se o formato é reconhecido e os dígitos verificadores dos
 *                        campos 1-3 batem (não validamos o DV geral/mod11 nesta v1).
 * @param bankCode        código do banco emissor (3 dígitos, ex.: "237" = Bradesco).
 * @param bankName        nome amigável do banco, quando reconhecido, senão "Banco {code}".
 * @param amount          valor do boleto em reais, ou null quando o campo vem zerado
 *                        (alguns boletos deixam o pagador informar o valor).
 * @param dueDate          data de vencimento, ou null quando o fator de vencimento é 0000
 *                        (sem vencimento definido).
 * @param fieldChecksumsOk true se os 3 dígitos verificadores de campo (mod10) conferem.
 * @param message          motivo de falha, quando valid=false.
 */
public record BoletoLinhaDigitavel(
        boolean valid,
        String bankCode,
        String bankName,
        BigDecimal amount,
        LocalDate dueDate,
        boolean fieldChecksumsOk,
        String message
) {
    public static BoletoLinhaDigitavel invalid(String message) {
        return new BoletoLinhaDigitavel(false, null, null, null, null, false, message);
    }
}
