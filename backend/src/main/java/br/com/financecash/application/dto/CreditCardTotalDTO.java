package br.com.financecash.application.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Quebra do totalizador de Lançamentos por cartão — a pedido do Marcio (01/10, quinta rodada):
 * "quero ver o totalizador por cartão e por banco". O frontend agrupa por {@code bankName} pra
 * mostrar o "por banco" (um usuário pode ter mais de um cartão do mesmo banco). Em regime de
 * CAIXA, {@code receita} vem sempre zero — o valor de InvoiceSettlement já é o líquido que o
 * banco informou pra fatura, não faz sentido separar despesa/receita dentro dele.
 */
public record CreditCardTotalDTO(
        UUID creditCardId,
        String displayName,
        String bankName,
        BigDecimal despesa,
        BigDecimal receita
) {
}
