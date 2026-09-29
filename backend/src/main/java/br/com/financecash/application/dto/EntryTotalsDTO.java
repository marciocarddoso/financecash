package br.com.financecash.application.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Totais de todo o resultado filtrado da busca de Lançamentos (não só a página atual) — a pedido
 * do Marcio (01/10), que reportou ter filtro na tela mas nenhum totalizador pra conferir o valor
 * de uma fatura de cartão contra o app do banco.
 *
 * <p>{@code totalDespesa}/{@code totalReceita}/{@code net} respeitam o {@code regime}
 * escolhido (ver TotalsRegime) — em COMPETENCIA é o comportamento original (tudo pelo dueDate);
 * em CAIXA, só o que já é dinheiro de verdade (lançamentos fora de cartão pagos, mais o que o
 * banco informou de fatura por cartão). {@code creditCardTotals} é a quebra por cartão nesse
 * mesmo regime (quinta rodada, 01/10, a pedido do Marcio: "separar as despesas de cartão do
 * totalizador... quero ver o totalizador por cartão e por banco") — não substitui os totais
 * gerais, só detalha; nada fica escondido. {@code bankSettlements} só vem preenchido quando a
 * busca filtra por um único cartão (creditCardId) — é o detalhe transação a transação do que o
 * banco informou como pagamento/financiamento daquela fatura.
 */
public record EntryTotalsDTO(
        TotalsRegime regime,
        BigDecimal totalDespesa,
        BigDecimal totalReceita,
        BigDecimal net,
        long entryCount,
        List<CreditCardTotalDTO> creditCardTotals,
        List<InvoiceSettlementDTO> bankSettlements
) {
}
