package br.com.financecash.application.dto;

import java.math.BigDecimal;

/**
 * Conta trazida ao vivo da Pluggy (PluggyClient.listAccounts) — não é persistida
 * no FinanceCash, é só pra exibição na tela "Bancos Conectados".
 */
public record BankAccountInfoDTO(
        String id,
        String type,
        String subtype,
        String number,
        String name,
        String marketingName,
        BigDecimal balance,
        String currencyCode
) {
}
