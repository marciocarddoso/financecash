package br.com.financecash.application.dto;

/**
 * Regime de apuração do totalizador da tela de Lançamentos (01/10, quinta rodada, a pedido do
 * Marcio, depois de explicar que sua planilha manual conta o gasto do cartão pelo valor do
 * PAGAMENTO da fatura, não pela data de cada compra — ver EntryService.buildTotals).
 */
public enum TotalsRegime {
    /** Comportamento original/padrão: tudo pelo dueDate (pra cartão, o vencimento do ciclo da fatura). */
    COMPETENCIA,
    /**
     * Só o que já é dinheiro de verdade: lançamentos fora de cartão pela data real de pagamento
     * (status PAGO + paymentDate), e cartão pelo valor que o próprio banco informou como
     * pago/financiado da fatura (InvoiceSettlement) — bate com a lógica da planilha do Marcio.
     */
    CAIXA
}
