package br.com.financecash.openfinance;

import br.com.financecash.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PluggyClientTest {

    private static final String BASE_URL = "https://api.pluggy.ai";

    private RestTemplate restTemplate;
    private MockRestServiceServer mockServer;

    private PluggyClient clientWith(String clientId, String clientSecret) {
        restTemplate = new RestTemplateBuilder().build();
        mockServer = MockRestServiceServer.bindTo(restTemplate).build();
        RestTemplateBuilder fixedBuilder = new RestTemplateBuilder() {
            @Override
            public RestTemplate build() {
                return restTemplate;
            }
        };
        return new PluggyClient(fixedBuilder, BASE_URL, clientId, clientSecret);
    }

    @BeforeEach
    void setUp() {
        // cada teste monta seu próprio client via clientWith(...)
    }

    @Test
    void deveAutenticarECriarConnectToken() {
        PluggyClient client = clientWith("meu-client-id", "meu-client-secret");

        mockServer.expect(requestTo(BASE_URL + "/auth"))
                .andExpect(jsonPath("$.clientId").value("meu-client-id"))
                .andExpect(jsonPath("$.clientSecret").value("meu-client-secret"))
                .andRespond(withSuccess("{\"apiKey\":\"api-key-123\"}", MediaType.APPLICATION_JSON));

        mockServer.expect(requestTo(BASE_URL + "/connect_token"))
                .andExpect(header("X-API-KEY", "api-key-123"))
                .andExpect(jsonPath("$.clientUserId").value("user-1"))
                .andRespond(withSuccess("{\"accessToken\":\"connect-token-abc\"}", MediaType.APPLICATION_JSON));

        String accessToken = client.createConnectToken("user-1");

        assertThat(accessToken).isEqualTo("connect-token-abc");
        mockServer.verify();
    }

    @Test
    void deveReutilizarApiKeyEmCacheEmChamadasSeguintes() {
        PluggyClient client = clientWith("meu-client-id", "meu-client-secret");

        mockServer.expect(requestTo(BASE_URL + "/auth"))
                .andRespond(withSuccess("{\"apiKey\":\"api-key-123\"}", MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo(BASE_URL + "/connect_token"))
                .andRespond(withSuccess("{\"accessToken\":\"token-1\"}", MediaType.APPLICATION_JSON));
        // segunda chamada não deve repetir /auth — só mais um /connect_token esperado
        mockServer.expect(requestTo(BASE_URL + "/connect_token"))
                .andRespond(withSuccess("{\"accessToken\":\"token-2\"}", MediaType.APPLICATION_JSON));

        assertThat(client.createConnectToken("user-1")).isEqualTo("token-1");
        assertThat(client.createConnectToken("user-1")).isEqualTo("token-2");

        mockServer.verify();
    }

    @Test
    void deveBuscarDetalhesDoItem() {
        PluggyClient client = clientWith("meu-client-id", "meu-client-secret");

        mockServer.expect(requestTo(BASE_URL + "/auth"))
                .andRespond(withSuccess("{\"apiKey\":\"api-key-123\"}", MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo(BASE_URL + "/items/item-1"))
                .andExpect(header("X-API-KEY", "api-key-123"))
                .andRespond(withSuccess(
                        "{\"id\":\"item-1\",\"connector\":{\"name\":\"Nubank\"},\"status\":\"UPDATED\"}",
                        MediaType.APPLICATION_JSON));

        PluggyClient.ItemInfo itemInfo = client.getItem("item-1");

        assertThat(itemInfo.itemId()).isEqualTo("item-1");
        assertThat(itemInfo.bankName()).isEqualTo("Nubank");
        assertThat(itemInfo.status()).isEqualTo("UPDATED");
        mockServer.verify();
    }

    @Test
    void deveLancarBusinessExceptionQuandoItemNaoEncontrado() {
        PluggyClient client = clientWith("meu-client-id", "meu-client-secret");

        mockServer.expect(requestTo(BASE_URL + "/auth"))
                .andRespond(withSuccess("{\"apiKey\":\"api-key-123\"}", MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo(BASE_URL + "/items/item-inexistente"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.getItem("item-inexistente"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void deveLancarBusinessExceptionQuandoCredenciaisNaoConfiguradas() {
        PluggyClient client = clientWith("", "");

        assertThatThrownBy(() -> client.createConnectToken("user-1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("PLUGGY_CLIENT_ID");
    }

    @Test
    void deveListarTransacoesUsandoEndpointV2ComCursor() {
        // Achado em produção: o endpoint antigo GET /transactions está desativado (410 Gone,
        // ENDPOINT_DEPRECATED) — a Pluggy orienta usar GET /v2/transactions, com dateFrom/dateTo
        // no lugar de from/to. Esse teste trava a URL e os parâmetros certos pra não regredir.
        // Também confirma o parsing de creditCardMetadata (usado pra detectar parcelamento).
        PluggyClient client = clientWith("meu-client-id", "meu-client-secret");

        mockServer.expect(requestTo(BASE_URL + "/auth"))
                .andRespond(withSuccess("{\"apiKey\":\"api-key-123\"}", MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo(BASE_URL + "/v2/transactions?accountId=acc-1&dateFrom=2026-09-01&dateTo=2026-09-28"))
                .andExpect(header("X-API-KEY", "api-key-123"))
                .andRespond(withSuccess(
                        "{\"results\":[{\"id\":\"tx-1\",\"description\":\"Uber\",\"amount\":-35.90,"
                                + "\"date\":\"2026-09-14T10:00:00.000Z\",\"type\":\"DEBIT\",\"status\":\"POSTED\","
                                + "\"category\":null,\"creditCardMetadata\":{\"installmentNumber\":3,"
                                + "\"totalInstallments\":12,\"purchaseDate\":\"2026-07-14\"}}],\"next\":null}",
                        MediaType.APPLICATION_JSON));

        List<PluggyClient.TransactionInfo> transactions = client.listTransactions(
                "acc-1", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 28));

        assertThat(transactions).hasSize(1);
        PluggyClient.TransactionInfo tx = transactions.get(0);
        assertThat(tx.id()).isEqualTo("tx-1");
        assertThat(tx.description()).isEqualTo("Uber");
        assertThat(tx.amount()).isEqualByComparingTo("-35.90");
        assertThat(tx.date()).isEqualTo(LocalDate.of(2026, 9, 14));
        assertThat(tx.status()).isEqualTo("POSTED");
        assertThat(tx.creditCardMetadata()).isNotNull();
        assertThat(tx.creditCardMetadata().installmentNumber()).isEqualTo(3);
        assertThat(tx.creditCardMetadata().totalInstallments()).isEqualTo(12);
        assertThat(tx.creditCardMetadata().purchaseDate()).isEqualTo(LocalDate.of(2026, 7, 14));
        mockServer.verify();
    }

    @Test
    void deveLancarBusinessExceptionQuandoPluggyRetornaErro() {
        PluggyClient client = clientWith("meu-client-id", "meu-client-secret");

        mockServer.expect(requestTo(BASE_URL + "/auth"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.createConnectToken("user-1"))
                .isInstanceOf(BusinessException.class);
    }
}
