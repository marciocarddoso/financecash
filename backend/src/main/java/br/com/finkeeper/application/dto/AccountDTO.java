package br.com.finkeeper.application.dto;

import br.com.finkeeper.domain.model.Account;
import br.com.finkeeper.domain.model.AccountType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record AccountDTO(
        UUID id,
        String name,
        String bankName,
        AccountType type,
        String investmentDescription,
        boolean active,
        BigDecimal latestBalance,
        LocalDate latestBalanceDate
) {
    public static AccountDTO from(Account account, BigDecimal latestBalance, LocalDate latestBalanceDate) {
        return new AccountDTO(
                account.getId(), account.getName(), account.getBankName(), account.getType(),
                account.getInvestmentDescription(), account.isActive(), latestBalance, latestBalanceDate);
    }
}
