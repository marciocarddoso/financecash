package br.com.financecash.application.dto;

import br.com.financecash.domain.model.CreditCard;
import br.com.financecash.openfinance.CreditCardDisplayNameFormatter;

import java.util.UUID;

/**
 * {@code displayName} é o nome curto pra exibição ("Bradesco Visa") e {@code bankShortName} é só
 * a parte do banco normalizada ("Bradesco") — usado pelo frontend pra rotular o filtro "por
 * banco" sem repetir a lógica de normalização (ver CreditCardDisplayNameFormatter). O filtro em
 * si continua enviando o {@code bankName} cru pro backend (ver EntryController/EntryRepository),
 * porque é contra esse valor cru que a busca compara. {@code name}/{@code bankName} continuam
 * expostos crus (usados na tela de Cartões pra edição manual) — 01/10, quinta rodada.
 */
public record CreditCardDTO(UUID id, String name, String bankName, String brand, String displayName,
                             String bankShortName, int closingDay, int dueDay, boolean active) {
    public static CreditCardDTO from(CreditCard card) {
        return new CreditCardDTO(
                card.getId(),
                card.getName(),
                card.getBankName(),
                card.getBrand(),
                CreditCardDisplayNameFormatter.format(card.getBankName(), card.getBrand()),
                CreditCardDisplayNameFormatter.shortBankName(card.getBankName()),
                card.getClosingDay(),
                card.getDueDay(),
                card.isActive());
    }
}
