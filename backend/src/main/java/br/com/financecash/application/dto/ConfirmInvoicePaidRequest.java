package br.com.financecash.application.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

/**
 * @param paymentDate opcional — quando omitido, EntryService.confirmInvoicePaid usa o próprio
 *                     dueDate da fatura como data de pagamento (melhor palpite disponível; a
 *                     Pluggy não informa a data real do pagamento, só que a fatura existe).
 */
public record ConfirmInvoicePaidRequest(
        @NotNull UUID creditCardId,
        @NotNull LocalDate dueDate,
        LocalDate paymentDate) {
}
