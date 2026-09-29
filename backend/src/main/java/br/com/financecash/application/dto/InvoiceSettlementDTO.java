package br.com.financecash.application.dto;

import br.com.financecash.domain.model.InvoiceSettlement;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * O que o banco informou como pagamento/financiamento de uma fatura, segundo a Pluggy — ver
 * InvoiceSettlement. Exposto junto do total calculado (EntryTotalsDTO) pra conferência manual
 * na tela de Lançamentos quando filtrada por um cartão específico.
 */
public record InvoiceSettlementDTO(
        BigDecimal amount,
        LocalDate transactionDate,
        LocalDate cycleDueDate,
        String description
) {
    public static InvoiceSettlementDTO from(InvoiceSettlement settlement) {
        return new InvoiceSettlementDTO(
                settlement.getAmount(), settlement.getTransactionDate(),
                settlement.getCycleDueDate(), settlement.getDescription());
    }
}
