package br.com.financecash.openfinance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Traduz as categorias que a Pluggy devolve nas transações (campo {@code category}) pra
 * português — usado pelo TransactionImportService antes de resolver/criar a Category.
 *
 * <p><strong>Achado em produção (30/09)</strong>: a documentação anterior dizia que
 * {@code category} só vinha em planos Pro+ da Pluggy, então o código assumia que ficaria
 * {@code null} no plano gratuito do Marcio e caía direto na categoria genérica "Outros". Não
 * era verdade — o campo vem preenchido mesmo no plano gratuito, só que em inglês (taxonomia
 * fixa da Pluggy, documentada em docs.pluggy.ai/docs/transaction-categories), e sem tradução
 * as categorias ficavam em inglês no FinanceCash, misturadas com as que o Marcio cria
 * manualmente em português.</p>
 *
 * <p>Mapa monta a partir da documentação (não contra todos os valores reais ainda — a doc já
 * se mostrou incompleta/desatualizada outras vezes nessa integração), cobrindo as categorias
 * de nível 1 e nível 2 da taxonomia. Um nome que não estiver no mapa passa direto sem
 * tradução (nunca quebra a importação por causa de uma categoria nova/desconhecida) — se
 * aparecer algum caso assim, é só adicionar aqui.</p>
 *
 * <p><strong>Achado em produção (01/10)</strong>: o Marcio reportou categorias duplicadas (a
 * mesma categoria em inglês E em português) depois da janela de sincronização subir de 120 pra
 * 365 dias — mais histórico importado expôs subcategorias que nunca tinham aparecido antes.
 * Comparando o mapa contra a lista completa da doc (não só contra o que já tinha sido visto),
 * duas seções inteiras estavam faltando: as 4 subcategorias de "Insurance" (nível 1 já estava
 * traduzido, nenhuma subcategoria estava) e as 2 de "Legal obligations". Cada uma dessas, sem
 * tradução, virava uma Category nova em inglês em vez de cair numa já existente em português —
 * essa é a causa raiz da duplicação, não um bug de comparação (translate() já era
 * case-insensitive). Duplicatas já criadas antes desse fix não são limpas automaticamente por
 * esta classe — ver TransactionImportService/ROADMAP para o script de correção dos dados já
 * importados.</p>
 *
 * <p><strong>Achado em produção (01/10, segunda rodada)</strong>: comparando o mapa contra a
 * tabela REAL de categorias do Marcio (não só contra a doc), a doc também estava incompleta —
 * faltavam subcategorias inteiras que nem aparecem nela ("Cinema, theater and concerts",
 * "Electricity", "Gas stations", "Mobile", "School", "Tolls and in vehicle payment",
 * "University", "Vehicle maintenance") e uma com grafia diferente da documentada
 * ("Accomodation", não "Accommodation"). Mesma cautela de sempre com a doc da Pluggy —
 * confirmar contra dado real sempre que possível, em vez de assumir a doc completa.</p>
 */
public final class PluggyCategoryTranslator {

    private static final Logger log = LoggerFactory.getLogger(PluggyCategoryTranslator.class);

    private static final Map<String, String> TRANSLATIONS = Map.<String, String>ofEntries(
            // Nível 1
            Map.entry("income", "Renda"),
            Map.entry("loans and financing", "Empréstimos e Financiamentos"),
            Map.entry("investments", "Investimentos"),
            Map.entry("same person transfer", "Transferência entre contas próprias"),
            Map.entry("transfers", "Transferências"),
            Map.entry("legal obligations", "Obrigações legais"),
            Map.entry("services", "Serviços"),
            Map.entry("shopping", "Compras"),
            Map.entry("digital services", "Serviços digitais"),
            Map.entry("groceries", "Mercado"),
            Map.entry("food and drinks", "Alimentação"),
            Map.entry("travel", "Viagem"),
            Map.entry("donations", "Doações"),
            Map.entry("gambling", "Jogos de azar"),
            Map.entry("taxes", "Impostos"),
            Map.entry("bank fees", "Tarifas bancárias"),
            Map.entry("housing", "Moradia"),
            Map.entry("healthcare", "Saúde"),
            Map.entry("transportation", "Transporte"),
            Map.entry("insurance", "Seguros"),
            Map.entry("leisure", "Lazer"),
            Map.entry("other", "Outros"),
            // Nível 2 — Income
            Map.entry("salary", "Salário"),
            Map.entry("retirement", "Aposentadoria"),
            Map.entry("entrepreneurial activities", "Atividades empreendedoras"),
            Map.entry("government aid", "Auxílio governamental"),
            Map.entry("non-recurring income", "Renda não recorrente"),
            // Nível 2 — Loans and Financing
            Map.entry("late payment and overdraft costs", "Multas e juros por atraso"),
            Map.entry("interests charged", "Juros cobrados"),
            Map.entry("loans", "Empréstimos"),
            Map.entry("financing", "Financiamentos"),
            // Nível 2 — Investments
            Map.entry("automatic investment", "Investimento automático"),
            Map.entry("fixed income", "Renda fixa"),
            Map.entry("mutual funds", "Fundos de investimento"),
            Map.entry("variable income", "Renda variável"),
            Map.entry("margin", "Margem"),
            Map.entry("proceeds interests and dividends", "Rendimentos e dividendos"),
            Map.entry("pension", "Previdência"),
            // Nível 2 — Same person transfer
            Map.entry("same person transfer - cash", "Transferência própria - Dinheiro"),
            Map.entry("same person transfer - pix", "Transferência própria - PIX"),
            Map.entry("same person transfer - ted", "Transferência própria - TED"),
            // Nível 2 — Transfers
            Map.entry("transfer - bank slip (boleto)", "Transferência - Boleto"),
            Map.entry("transfer - cash", "Transferência - Dinheiro"),
            Map.entry("transfer - check", "Transferência - Cheque"),
            Map.entry("transfer - doc", "Transferência - DOC"),
            Map.entry("transfer - foreign exchange", "Transferência - Câmbio"),
            Map.entry("transfer - internal", "Transferência interna"),
            Map.entry("transfer - pix", "Transferência - PIX"),
            Map.entry("transfer - ted", "Transferência - TED"),
            Map.entry("credit card payment", "Pagamento de fatura de cartão"),
            Map.entry("third-party transfers", "Transferência para terceiros"),
            // Nível 2 — Services
            Map.entry("telecommunications", "Telecomunicações"),
            Map.entry("education", "Educação"),
            Map.entry("wellness and fitness", "Bem-estar e academia"),
            Map.entry("tickets", "Ingressos")
    );

    // Segundo bloco: Map.ofEntries tem limite prático de legibilidade, não de tamanho — mas
    // separar em duas constantes e mesclar deixa o diff mais fácil de revisar por seção.
    private static final Map<String, String> TRANSLATIONS_2 = Map.<String, String>ofEntries(
            // Nível 2 — Shopping
            Map.entry("online shopping", "Compras online"),
            Map.entry("electronics", "Eletrônicos"),
            Map.entry("pet supplies and vet", "Pet shop e veterinário"),
            Map.entry("clothing", "Vestuário"),
            Map.entry("kids and toys", "Infantil e brinquedos"),
            Map.entry("bookstore", "Livraria"),
            Map.entry("sports goods", "Artigos esportivos"),
            Map.entry("office supplies", "Material de escritório"),
            Map.entry("cashback", "Cashback"),
            // Nível 2 — Digital services
            Map.entry("gaming", "Jogos"),
            Map.entry("video streaming", "Streaming de vídeo"),
            Map.entry("music streaming", "Streaming de música"),
            // Nível 2 — Housing
            Map.entry("rent", "Aluguel"),
            Map.entry("houseware", "Utilidades domésticas"),
            Map.entry("urban land and building tax", "IPTU"),
            Map.entry("utilities", "Contas de consumo (água/luz/gás)"),
            // Nível 2 — Transportation
            Map.entry("taxi and ride-hailing", "Táxi e aplicativos de transporte"),
            Map.entry("public transportation", "Transporte público"),
            Map.entry("car rental", "Aluguel de carro"),
            Map.entry("bicycle", "Bicicleta"),
            Map.entry("automotive", "Automotivo"),
            // Nível 2 — Healthcare
            Map.entry("dentist", "Dentista"),
            Map.entry("pharmacy", "Farmácia"),
            Map.entry("optometry", "Oftalmologia/Óptica"),
            Map.entry("hospital clinics and labs", "Hospitais, clínicas e laboratórios"),
            // Nível 2 — Food and drinks
            Map.entry("eating out", "Restaurantes"),
            Map.entry("food delivery", "Delivery de comida"),
            // Nível 2 — Travel
            Map.entry("airport and airlines", "Aeroporto e companhias aéreas"),
            Map.entry("accommodation", "Hospedagem"),
            Map.entry("mileage programs", "Programas de milhas"),
            Map.entry("bus tickets", "Passagens de ônibus"),
            // Nível 2 — Gambling
            Map.entry("lottery", "Loteria"),
            Map.entry("online bet", "Apostas online"),
            // Nível 2 — Taxes
            Map.entry("income taxes", "Imposto de renda"),
            Map.entry("taxes on investments", "Impostos sobre investimentos"),
            Map.entry("tax on financial operations", "IOF"),
            // Nível 2 — Bank fees
            Map.entry("account fees", "Tarifas de conta"),
            Map.entry("wire transfer fees and atm fees", "Tarifas de transferência e saque"),
            Map.entry("credit card fees", "Tarifas de cartão de crédito"),
            // Nível 2 — Legal obligations (faltava — achado em produção, 01/10, comparando
            // contra a lista completa em docs.pluggy.ai/docs/transaction-categories)
            Map.entry("blocked balances", "Saldos bloqueados"),
            Map.entry("alimony", "Pensão alimentícia"),
            // Nível 2 — Insurance (faltava inteiro — mesmo achado; nível 1 "insurance" já
            // estava traduzido, mas nenhuma subcategoria)
            Map.entry("life insurance", "Seguro de vida"),
            Map.entry("home insurance", "Seguro residencial"),
            Map.entry("health insurance", "Seguro saúde"),
            Map.entry("vehicle insurance", "Seguro veicular"),
            // Subcategorias que NÃO estão na doc (docs.pluggy.ai/docs/transaction-categories) —
            // achadas direto na tabela real de categorias do Marcio (01/10), depois de
            // confirmar que a doc, de novo, está incompleta (mesmo padrão de outras vezes
            // nessa integração — ver TransactionImportService). "Accomodation" é a grafia real
            // que a Pluggy manda (a doc documenta "Accommodation", com dois "m", mas o valor
            // real observado tem só um) — mapeado do jeito que realmente chega.
            Map.entry("accomodation", "Hospedagem"),
            Map.entry("cinema, theater and concerts", "Cinema, teatro e shows"),
            Map.entry("electricity", "Energia elétrica"),
            // Sem acento de propósito (01/10, a pedido do Marcio): ele já tinha uma categoria
            // manual "Combustivel" (sem acento) antes da Pluggy mandar "Gas stations" — usa o
            // nome exatamente como já existia em vez de criar uma categoria nova acentuada,
            // senão vira duplicata de novo (mesmo problema que estamos corrigindo).
            Map.entry("gas stations", "Combustivel"),
            Map.entry("mobile", "Celular"),
            Map.entry("school", "Escola"),
            Map.entry("tolls and in vehicle payment", "Pedágio e pagamento no veículo"),
            Map.entry("university", "Faculdade"),
            Map.entry("vehicle maintenance", "Manutenção veicular"),
            // "Transfer - Bank Slip" é a grafia real (sem o "(Boleto)" que a doc lista) —
            // mantém o mapeamento original também, caso a Pluggy mande as duas formas.
            Map.entry("transfer - bank slip", "Transferência - Boleto")
    );

    private PluggyCategoryTranslator() {
    }

    /**
     * Traduz o nome de categoria da Pluggy (case-insensitive) pra português. Retorna o valor
     * original, sem alterar, quando não há tradução conhecida — nunca lança exceção nem
     * retorna null pra um valor não-null de entrada.
     */
    public static String translate(String pluggyCategoryName) {
        if (pluggyCategoryName == null || pluggyCategoryName.isBlank()) {
            return pluggyCategoryName;
        }
        String key = pluggyCategoryName.trim().toLowerCase();
        String translated = TRANSLATIONS.get(key);
        if (translated != null) {
            return translated;
        }
        translated = TRANSLATIONS_2.get(key);
        if (translated != null) {
            return translated;
        }
        // Diagnóstico temporário (30/09): o Marcio reportou que ALGUMAS categorias ainda vêm em
        // inglês. Esse log mostra exatamente qual nome a Pluggy mandou e não bateu com o mapa
        // (provavelmente uma subcategoria fora da lista documentada) — é a partir dele que dá
        // pra completar o mapa com o valor real, em vez de adivinhar. Remover quando o mapa
        // estiver considerado completo.
        log.info("Categoria da Pluggy sem tradução conhecida: '{}'", pluggyCategoryName);
        return pluggyCategoryName;
    }
}
