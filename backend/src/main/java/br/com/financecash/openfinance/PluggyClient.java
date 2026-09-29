package br.com.financecash.openfinance;

import br.com.financecash.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

/**
 * Wrapper HTTP fino sobre a API REST da Pluggy (agregador de Open Finance) — ver
 * docs/OPEN-FINANCE-E-BOLETOS.md, seção 2, para o raciocínio por trás de usar um
 * agregador em vez de virar participante direto do Open Finance.
 *
 * Deliberadamente não usa o SDK oficial da Pluggy para Java (github.com/pluggyai/pluggy-java):
 * ele é publicado via GitHub Packages, o que exigiria configurar autenticação extra no
 * Maven só para resolver a dependência (mesmo sendo um pacote público). Como a API tem só
 * 4 endpoints relevantes para o FinanceCash hoje, um client próprio é mais simples de manter
 * e evita essa dependência extra.
 *
 * clientId/clientSecret nunca saem do backend — vêm de variáveis de ambiente
 * (PLUGGY_CLIENT_ID / PLUGGY_CLIENT_SECRET, ver application.yml). O apiKey obtido em troca
 * deles vale 2h e é cacheado em memória, renovado automaticamente quando expira.
 */
@Component
public class PluggyClient {

    private static final Logger log = LoggerFactory.getLogger(PluggyClient.class);
    private static final Duration API_KEY_TTL = Duration.ofHours(2);
    private static final Duration API_KEY_SAFETY_MARGIN = Duration.ofMinutes(5);

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String clientId;
    private final String clientSecret;

    private volatile String cachedApiKey;
    private volatile Instant apiKeyExpiresAt = Instant.EPOCH;

    public PluggyClient(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${financecash.pluggy.base-url:https://api.pluggy.ai}") String baseUrl,
            @Value("${financecash.pluggy.client-id:}") String clientId,
            @Value("${financecash.pluggy.client-secret:}") String clientSecret) {
        this.restTemplate = restTemplateBuilder.build();
        this.baseUrl = baseUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    /**
     * Cria um Connect Token de curta duração (30 min) que o frontend usa para abrir o
     * widget "Pluggy Connect" com segurança, sem nunca ver o clientId/clientSecret.
     *
     * @param clientUserId identificador do usuário do FinanceCash (opcional para a
     *                      Pluggy, mas útil para rastrear qual conexão pertence a quem
     *                      caso o sistema um dia tenha mais de um usuário).
     */
    public String createConnectToken(String clientUserId) {
        String apiKey = getApiKey();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-API-KEY", apiKey);

        Map<String, Object> body = (clientUserId == null || clientUserId.isBlank())
                ? Map.of()
                : Map.of("clientUserId", clientUserId);

        try {
            ConnectTokenApiResponse response = restTemplate.postForObject(
                    baseUrl + "/connect_token", new HttpEntity<>(body, headers), ConnectTokenApiResponse.class);
            if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
                throw new BusinessException("A Pluggy não retornou um accessToken válido para o Connect Token.");
            }
            return response.accessToken();
        } catch (RestClientException ex) {
            log.error("Falha ao criar Connect Token na Pluggy: {}", ex.getMessage(), ex);
            throw new BusinessException("Não foi possível criar o Connect Token da Pluggy. Tente novamente em instantes.");
        }
    }

    /**
     * Busca os detalhes de um Item (uma conexão bancária) já autorizado pelo usuário no
     * widget "Pluggy Connect". Usado ao persistir uma BankConnection, para descobrir o nome
     * do banco (connector.name) e o status atual da conexão sem depender do payload do
     * onSuccess do widget, que só garante o campo item.id.
     *
     * @param itemId identificador do Item devolvido pelo widget no callback onSuccess.
     */
    public ItemInfo getItem(String itemId) {
        String apiKey = getApiKey();

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-KEY", apiKey);

        try {
            PluggyItemApiResponse response = restTemplate.exchange(
                    baseUrl + "/items/" + itemId,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    PluggyItemApiResponse.class).getBody();
            if (response == null || response.id() == null) {
                throw new BusinessException("A Pluggy não retornou dados para o item " + itemId + ".");
            }
            String bankName = response.connector() != null ? response.connector().name() : null;
            if (bankName == null || bankName.isBlank()) {
                bankName = "Banco não identificado";
            }
            return new ItemInfo(response.id(), bankName, response.status());
        } catch (RestClientException ex) {
            log.error("Falha ao buscar item {} na Pluggy: {}", itemId, ex.getMessage(), ex);
            throw new BusinessException("Não foi possível consultar a conexão bancária na Pluggy. Tente novamente em instantes.");
        }
    }

    /**
     * Lista as contas (corrente, poupança, cartão) de um Item já conectado. Não é
     * persistida no FinanceCash — é consultada ao vivo sempre que a tela "Bancos
     * Conectados" pede o detalhe de uma conexão. A importação de contas (tipo BANK) como
     * Account/BalanceSnapshot, e de cartões (tipo CREDIT, usando creditData.balanceCloseDate/
     * balanceDueDate) como CreditCard, é feita pelo AccountSyncService, que chama este método
     * na hora de sincronizar; a importação de transações como Entry é feita pelo
     * TransactionImportService, que chama listTransactions para cada conta/cartão sincronizado.
     */
    public List<AccountInfo> listAccounts(String itemId) {
        String apiKey = getApiKey();

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-KEY", apiKey);

        try {
            PluggyAccountsApiResponse response = restTemplate.exchange(
                    baseUrl + "/accounts?itemId=" + itemId,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    PluggyAccountsApiResponse.class).getBody();
            if (response == null || response.results() == null) {
                return List.of();
            }
            return response.results().stream()
                    .map(a -> new AccountInfo(a.id(), a.type(), a.subtype(), a.number(), a.name(), a.marketingName(),
                            a.balance(), a.currencyCode(), toCreditDataInfo(a.creditData())))
                    .toList();
        } catch (RestClientException ex) {
            log.error("Falha ao listar contas do item {} na Pluggy: {}", itemId, ex.getMessage(), ex);
            throw new BusinessException("Não foi possível consultar as contas dessa conexão na Pluggy. Tente novamente em instantes.");
        }
    }

    /**
     * Lista as transações de uma conta (corrente/poupança/cartão) da Pluggy num intervalo de
     * datas. Usado pelo TransactionImportService para importar lançamentos automaticamente a
     * partir de um {@code accountId} já resolvido via listAccounts.
     *
     * <p><strong>Achado em produção</strong>: o endpoint antigo {@code GET /transactions} (com
     * {@code from}/{@code to}/{@code pageSize}) está desativado pela Pluggy — retorna
     * {@code 410 Gone} com {@code ENDPOINT_DEPRECATED}, orientando usar {@code GET
     * /v2/transactions} com paginação por cursor. Migrado para o v2, com os parâmetros
     * {@code dateFrom}/{@code dateTo} (em vez de {@code from}/{@code to}) — confirmado contra
     * docs.pluggy.ai/en/reference/transactions-list-by-cursor. Sem paginação por enquanto (v1
     * da nossa importação): o v2 já devolve até 500 transações por página (fixo, sem parâmetro
     * de tamanho), cobrindo o volume esperado de um usuário pessoa física num intervalo de
     * poucos dias/meses; o cursor {@code next} da resposta é ignorado por ora — se algum dia
     * uma conta tiver mais de 500 transações no intervalo sincronizado, essa página passará a
     * precisar seguir o cursor até {@code next} vir nulo.
     *
     * @param accountId identificador da conta na Pluggy (AccountInfo.id()).
     * @param from       data inicial (inclusive) do intervalo.
     * @param to         data final (inclusive) do intervalo.
     */
    public List<TransactionInfo> listTransactions(String accountId, LocalDate from, LocalDate to) {
        String apiKey = getApiKey();

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-KEY", apiKey);

        String url = baseUrl + "/v2/transactions?accountId=" + accountId
                + "&dateFrom=" + from + "&dateTo=" + to;

        try {
            PluggyTransactionsApiResponse response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    PluggyTransactionsApiResponse.class).getBody();
            if (response == null || response.results() == null) {
                return List.of();
            }
            return response.results().stream()
                    .map(t -> new TransactionInfo(t.id(), t.description(), t.amount(),
                            t.date() != null ? t.date().atZone(ZoneOffset.UTC).toLocalDate() : null,
                            t.type(), t.status(), t.category(), toCreditCardMetadataInfo(t.creditCardMetadata())))
                    .toList();
        } catch (RestClientException ex) {
            log.error("Falha ao listar transações da conta {} na Pluggy: {}", accountId, ex.getMessage(), ex);
            throw new BusinessException("Não foi possível consultar as transações dessa conta na Pluggy. Tente novamente em instantes.");
        }
    }

    private CreditCardMetadataInfo toCreditCardMetadataInfo(PluggyCreditCardMetadataApiResponse metadata) {
        if (metadata == null) {
            return null;
        }
        return new CreditCardMetadataInfo(metadata.installmentNumber(), metadata.totalInstallments(), metadata.purchaseDate());
    }

    private CreditDataInfo toCreditDataInfo(PluggyCreditDataApiResponse creditData) {
        if (creditData == null) {
            return null;
        }
        return new CreditDataInfo(creditData.brand(), creditData.balanceCloseDate(), creditData.balanceDueDate(),
                creditData.creditLimit(), creditData.availableCreditLimit());
    }

    /** Retorna o apiKey em cache, renovando via POST /auth se estiver ausente/expirado. */
    private synchronized String getApiKey() {
        if (clientId.isBlank() || clientSecret.isBlank()) {
            throw new BusinessException(
                    "Integração com a Pluggy não configurada. Defina PLUGGY_CLIENT_ID e PLUGGY_CLIENT_SECRET "
                            + "nas variáveis de ambiente do backend (ver docs/OPEN-FINANCE-E-BOLETOS.md).");
        }
        if (cachedApiKey != null && Instant.now().isBefore(apiKeyExpiresAt.minus(API_KEY_SAFETY_MARGIN))) {
            return cachedApiKey;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, String> body = Map.of("clientId", clientId, "clientSecret", clientSecret);

        try {
            AuthApiResponse response = restTemplate.postForObject(
                    baseUrl + "/auth", new HttpEntity<>(body, headers), AuthApiResponse.class);
            if (response == null || response.apiKey() == null || response.apiKey().isBlank()) {
                throw new BusinessException("A Pluggy não retornou um apiKey válido na autenticação.");
            }
            cachedApiKey = response.apiKey();
            apiKeyExpiresAt = Instant.now().plus(API_KEY_TTL);
            log.info("Novo apiKey da Pluggy obtido, válido até {}.", apiKeyExpiresAt);
            return cachedApiKey;
        } catch (RestClientException ex) {
            log.error("Falha ao autenticar na Pluggy: {}", ex.getMessage(), ex);
            throw new BusinessException("Não foi possível autenticar na Pluggy. Verifique as credenciais configuradas.");
        }
    }

    private record AuthApiResponse(String apiKey) {
    }

    private record ConnectTokenApiResponse(String accessToken) {
    }

    /** Dados de um Item da Pluggy relevantes para o FinanceCash (ver getItem). */
    public record ItemInfo(String itemId, String bankName, String status) {
    }

    private record PluggyItemApiResponse(String id, ConnectorApiResponse connector, String status) {
    }

    private record ConnectorApiResponse(String name) {
    }

    /** Conta trazida ao vivo da Pluggy — ver listAccounts. creditData só vem preenchido para type=CREDIT. */
    public record AccountInfo(
            String id, String type, String subtype, String number, String name,
            String marketingName, BigDecimal balance, String currencyCode, CreditDataInfo creditData) {
    }

    /**
     * Dados de fatura de um cartão de crédito (type=CREDIT) — usado pelo AccountSyncService
     * pra sincronizar CreditCard.closingDay/dueDay a partir de balanceCloseDate/balanceDueDate
     * (dia do mês extraído da data da fatura atual). Campos confirmados em docs.pluggy.ai/docs/accounts.
     */
    public record CreditDataInfo(
            String brand, LocalDate balanceCloseDate, LocalDate balanceDueDate,
            BigDecimal creditLimit, BigDecimal availableCreditLimit) {
    }

    /**
     * Transação trazida ao vivo da Pluggy — ver listTransactions. {@code amount} segue a
     * convenção da Pluggy: para contas correntes/poupança, positivo = entrada e negativo =
     * saída; para cartão de crédito, positivo = compra/débito na fatura e negativo =
     * pagamento/estorno. {@code category} só vem preenchido em planos Pro+ da Pluggy — pode vir
     * null no plano gratuito, caso em que o TransactionImportService usa uma categoria genérica.
     */
    public record TransactionInfo(
            String id, String description, BigDecimal amount, LocalDate date,
            String type, String status, String category, CreditCardMetadataInfo creditCardMetadata) {
    }

    /**
     * Metadados extras de uma transação de cartão de crédito — usado pelo
     * TransactionImportService pra detectar compras parceladas (totalInstallments &gt; 1).
     * Confirmado só contra a documentação (docs.pluggy.ai/docs/transactions), ainda não contra
     * dado real do MeuPluggy: {@code totalAmount} (soma de todas as parcelas) é documentado como
     * indisponível em conectores Open Finance como o do Marcio, por isso nem foi mapeado aqui —
     * só os campos que a doc não restringe por tipo de conector.
     */
    public record CreditCardMetadataInfo(Integer installmentNumber, Integer totalInstallments, LocalDate purchaseDate) {
    }

    private record PluggyTransactionsApiResponse(List<PluggyTransactionApiResponse> results) {
    }

    private record PluggyTransactionApiResponse(
            String id, String description, BigDecimal amount, Instant date,
            String type, String status, String category, PluggyCreditCardMetadataApiResponse creditCardMetadata) {
    }

    private record PluggyCreditCardMetadataApiResponse(
            Integer installmentNumber, Integer totalInstallments, LocalDate purchaseDate) {
    }

    private record PluggyAccountsApiResponse(List<PluggyAccountApiResponse> results) {
    }

    private record PluggyAccountApiResponse(
            String id, String type, String subtype, String number, String name,
            String marketingName, BigDecimal balance, String currencyCode, PluggyCreditDataApiResponse creditData) {
    }

    private record PluggyCreditDataApiResponse(
            String brand, LocalDate balanceCloseDate, LocalDate balanceDueDate,
            BigDecimal creditLimit, BigDecimal availableCreditLimit) {
    }
}
