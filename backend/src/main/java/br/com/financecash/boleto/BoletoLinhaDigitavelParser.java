package br.com.financecash.boleto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * Decodifica a linha digitável de um boleto bancário (cobrança, 47 dígitos — não
 * cobre boleto de concessionária/convênio, que tem 48 dígitos e outro algoritmo).
 *
 * Contexto (ver docs/OPEN-FINANCE-E-BOLETOS.md): em vez de digitar manualmente
 * descrição/valor/vencimento de cada boleto, o usuário cola a linha digitável (a
 * sequência de números embaixo do código de barras) e o sistema pré-preenche o
 * lançamento. É o primeiro passo da Fase 2 do roadmap ("importação de boletos"),
 * sem depender de nenhuma integração externa.
 *
 * <p><b>Fator de vencimento:</b> desde 22/02/2025 a FEBRABAN mudou a regra porque o
 * campo de 4 dígitos (base 07/10/1997) estourou o limite de 9999 em 21/02/2025. A
 * partir de 22/02/2025 o fator reinicia em 1000 e incrementa 1 por dia
 * (22/02/2025 = 1000, 23/02/2025 = 1001, ...). Como qualquer boleto emitido a partir
 * de aqui usa exclusivamente essa nova era, este parser assume sempre a era nova —
 * um fator abaixo de 1000 é tratado como inválido/não suportado (seria um boleto
 * emitido antes da mudança, o que não faz sentido para um lançamento novo).</p>
 */
public final class BoletoLinhaDigitavelParser {

    private static final LocalDate FATOR_RESET_DATE = LocalDate.of(2025, 2, 22);
    private static final int FATOR_RESET_VALUE = 1000;

    private static final Map<String, String> BANK_NAMES = Map.ofEntries(
            Map.entry("001", "Banco do Brasil"),
            Map.entry("033", "Santander"),
            Map.entry("077", "Banco Inter"),
            Map.entry("104", "Caixa Econômica Federal"),
            Map.entry("237", "Bradesco"),
            Map.entry("260", "Nubank"),
            Map.entry("336", "C6 Bank"),
            Map.entry("341", "Itaú"),
            Map.entry("380", "PicPay"),
            Map.entry("422", "Banco Safra"),
            Map.entry("655", "Banco Votorantim")
    );

    private BoletoLinhaDigitavelParser() {
    }

    public static BoletoLinhaDigitavel parse(String rawInput) {
        if (rawInput == null || rawInput.isBlank()) {
            return BoletoLinhaDigitavel.invalid("Informe a linha digitável do boleto.");
        }

        String digits = rawInput.replaceAll("[^0-9]", "");

        if (digits.length() == 48) {
            return BoletoLinhaDigitavel.invalid(
                    "Linha digitável de 48 dígitos (boleto de concessionária/convênio) ainda não é suportada.");
        }
        if (digits.length() != 47) {
            return BoletoLinhaDigitavel.invalid(
                    "Linha digitável inválida: esperados 47 dígitos, encontrados " + digits.length() + ".");
        }

        String campo1Content = digits.substring(0, 9);
        char campo1Dv = digits.charAt(9);
        String campo2Content = digits.substring(10, 20);
        char campo2Dv = digits.charAt(20);
        String campo3Content = digits.substring(21, 31);
        char campo3Dv = digits.charAt(31);
        // campo4 (posição 32) é o DV geral (mod11) — não validado nesta v1.
        String fatorStr = digits.substring(33, 37);
        String valorStr = digits.substring(37, 47);

        boolean dv1Ok = mod10(campo1Content) == Character.getNumericValue(campo1Dv);
        boolean dv2Ok = mod10(campo2Content) == Character.getNumericValue(campo2Dv);
        boolean dv3Ok = mod10(campo3Content) == Character.getNumericValue(campo3Dv);
        boolean checksumsOk = dv1Ok && dv2Ok && dv3Ok;

        String bankCode = digits.substring(0, 3);
        String bankName = BANK_NAMES.getOrDefault(bankCode, "Banco " + bankCode);

        int fator = Integer.parseInt(fatorStr);
        LocalDate dueDate = fator >= FATOR_RESET_VALUE
                ? FATOR_RESET_DATE.plusDays(fator - FATOR_RESET_VALUE)
                : null;

        long valorCentavos = Long.parseLong(valorStr);
        BigDecimal amount = valorCentavos > 0
                ? BigDecimal.valueOf(valorCentavos, 2)
                : null;

        String message = checksumsOk ? null
                : "Os dígitos verificadores dos campos não conferem — confira se a linha digitável foi colada corretamente.";

        return new BoletoLinhaDigitavel(checksumsOk, bankCode, bankName, amount, dueDate, checksumsOk, message);
    }

    /**
     * Módulo 10 (Luhn) usado para os dígitos verificadores dos campos 1-3 da linha
     * digitável: pesos alternados 2 e 1 da direita para a esquerda; quando o produto
     * é >= 10, soma os dois algarismos do resultado.
     */
    static int mod10(String digits) {
        int total = 0;
        int weight = 2;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int d = Character.getNumericValue(digits.charAt(i));
            int product = d * weight;
            if (product >= 10) {
                product = product / 10 + product % 10;
            }
            total += product;
            weight = (weight == 2) ? 1 : 2;
        }
        return (10 - (total % 10)) % 10;
    }
}
