package br.com.financecash.openfinance;

import br.com.financecash.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

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
    void deveLancarBusinessExceptionQuandoPluggyRetornaErro() {
        PluggyClient client = clientWith("meu-client-id", "meu-client-secret");

        mockServer.expect(requestTo(BASE_URL + "/auth"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.createConnectToken("user-1"))
                .isInstanceOf(BusinessException.class);
    }
}
