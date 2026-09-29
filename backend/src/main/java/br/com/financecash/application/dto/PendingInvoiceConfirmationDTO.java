package br.com.financecash.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Uma fatura de cartão fechada (vencimento já passou) que ainda tem lançamentos PENDENTE —
 * candidata a "confirmar que foi paga" na tela de Dashboard/Lançamentos.
 *
 * <p><strong>Por que isso existe (01/10, a pedido do Marcio)</strong>: compras de cartão
 * importadas nascem PENDENTE e nada as marca como pagas automaticamente — nem quando a fatura
 * real já fechou/foi paga, porque a Pluggy não manda um sinal confiável disso (o vencimento da
 * fatura avança todo mês, pago ou não). Isso inflava "Falta pagar no mês" do dashboard com
 * faturas antigas já pagas na vida real. A alternativa de marcar como pago sozinho (só porque o
 * ciclo virou) foi descartada: esconderia um caso real de fatura em aberto (rotativo) sem
 * avisar o usuário — o Marcio escolheu confirmação manual em vez de automática, pelo mesmo
 * motivo que outras heurísticas arriscadas nessa integração não foram implementadas sozinhas
 * (ver TransactionImportService). Esse DTO é o que alimenta o aviso "fatura tal venceu, já foi
 * paga?"; confirmar = EntryService.confirmInvoicePaid.
 */
public record PendingInvoiceConfirmationDTO(
        UUID creditCardId,
        String creditCardName,
        String creditCardBankName,
        String creditCardDisplayName,
        LocalDate dueDate,
        int entryCount,
        BigDecimal totalAmount) {
}
