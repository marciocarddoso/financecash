package br.com.financecash.application.dto;

import br.com.financecash.domain.model.CreditCard;

import java.util.UUID;

public record CreditCardDTO(UUID id, String name, String bankName, int closingDay, int dueDay, boolean active) {
    public static CreditCardDTO from(CreditCard card) {
        return new CreditCardDTO(card.getId(), card.getName(), card.getBankName(), card.getClosingDay(), card.getDueDay(), card.isActive());
    }
}
