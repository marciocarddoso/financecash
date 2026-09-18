package br.com.financecash.application.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * "Fechamento do mês": previsto vs. realizado — ver Javadoc de MonthClosingService
 * para a definição exata de cada um.
 */
public record MonthClosingResponse(
        int year,
        int month,
        BigDecimal previstoReceita,
        BigDecimal realizadoReceita,
        BigDecimal previstoDespesa,
        BigDecimal realizadoDespesa,
        List<MonthClosingCategoryItem> categorias
) {
}
