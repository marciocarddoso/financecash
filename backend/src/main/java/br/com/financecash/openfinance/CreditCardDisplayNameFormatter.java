package br.com.financecash.openfinance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Monta um nome curto de exibição pra cartão ("Bradesco Visa", "Nubank Mastercard", "C6
 * Mastercard") a partir do {@code bankName} cru salvo em CreditCard (o que a Pluggy manda como
 * marketingName/name da conta bancária vinculada — ver AccountSyncService.resolveBankName, ex.
 * "Banco Bradesco") e da bandeira ({@code creditData.brand()} da Pluggy, ex. "VISA").
 *
 * <p><strong>Achado em produção (01/10, quinta rodada)</strong>: a tela de Lançamentos mostrava
 * "Banco Bradesco - Cartão (VISA) — Banco Bradesco" — o nome bruto do cartão (já formatado por
 * AccountSyncService.friendlyCardName) concatenado de novo com o bankName no frontend, feio e
 * redundante. O Marcio pediu só "banco + bandeira" (ex. "bradesco visa"), sem repetir "Banco"
 * nem "Cartão". Como não dá pra confirmar contra dado real de produção o texto exato que a
 * Pluggy manda de bankName pra cada instituição (varia pelo marketingName da conta corrente
 * vinculada, não por um catálogo fixo — mesmo problema já visto com categoria em inglês), a
 * normalização usa reconhecimento por palavra-chave ("contém", case-insensitive) em vez de
 * comparação exata — mais resiliente a variação de texto real — e loga quando cai no fallback
 * (mesmo padrão do PluggyCategoryTranslator) pra completar a lista com dado real depois, em vez
 * de adivinhar pra sempre.
 */
public final class CreditCardDisplayNameFormatter {

    private static final Logger log = LoggerFactory.getLogger(CreditCardDisplayNameFormatter.class);

    /** Ordem importa: primeiro match (case-insensitive, "contém") vence. */
    private static final Map<String, String> BANK_NAME_KEYWORDS = buildBankKeywords();

    private static Map<String, String> buildBankKeywords() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("bradesco", "Bradesco");
        map.put("nu pagamentos", "Nubank");
        map.put("nubank", "Nubank");
        map.put("c6", "C6");
        map.put("itaú", "Itaú");
        map.put("itau", "Itaú");
        map.put("santander", "Santander");
        map.put("caixa econômica", "Caixa");
        map.put("caixa economica", "Caixa");
        map.put("banco do brasil", "Banco do Brasil");
        map.put("inter", "Inter");
        return map;
    }

    private static final Map<String, String> BRAND_LABELS = Map.of(
            "visa", "Visa",
            "mastercard", "Mastercard",
            "elo", "Elo",
            "amex", "Amex",
            "american express", "Amex",
            "hipercard", "Hipercard"
    );

    private CreditCardDisplayNameFormatter() {
    }

    /**
     * Nome curto do banco pra exibição ("Banco Bradesco" vira "Bradesco"). Nunca retorna null
     * pra entrada não-null/não-vazia.
     */
    public static String shortBankName(String rawBankName) {
        if (rawBankName == null || rawBankName.isBlank()) {
            return rawBankName;
        }
        String lower = rawBankName.trim().toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> entry : BANK_NAME_KEYWORDS.entrySet()) {
            if (lower.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        log.info("Nome de banco sem normalização conhecida: '{}' — usando o valor original (sem prefixo 'Banco ').", rawBankName);
        // Fallback: tira um "Banco " no início, se tiver, em vez de mostrar o nome cru inteiro.
        String withoutPrefix = rawBankName.trim().replaceFirst("(?i)^banco\\s+", "");
        return withoutPrefix.isBlank() ? rawBankName.trim() : withoutPrefix;
    }

    private static String brandLabel(String rawBrand) {
        if (rawBrand == null || rawBrand.isBlank()) {
            return null;
        }
        String lower = rawBrand.trim().toLowerCase(Locale.ROOT);
        String known = BRAND_LABELS.get(lower);
        if (known != null) {
            return known;
        }
        log.info("Bandeira de cartão sem rótulo conhecido: '{}' — usando capitalização simples.", rawBrand);
        String trimmed = rawBrand.trim();
        return trimmed.substring(0, 1).toUpperCase(Locale.ROOT) + trimmed.substring(1).toLowerCase(Locale.ROOT);
    }

    /** "Bradesco Visa", "Nubank Mastercard", ou só "C6" quando não há bandeira conhecida ainda. */
    public static String format(String rawBankName, String rawBrand) {
        String bank = shortBankName(rawBankName);
        String brand = brandLabel(rawBrand);
        if (bank == null || bank.isBlank()) {
            return brand;
        }
        return brand != null ? bank + " " + brand : bank;
    }
}
