package br.com.financecash.api.controller;

import br.com.financecash.application.dto.ConnectTokenResponse;
import br.com.financecash.domain.model.AppUser;
import br.com.financecash.openfinance.PluggyClient;
import br.com.financecash.security.CurrentUserProvider;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Primeira peça da integração com Open Finance via Pluggy — ver
 * docs/OPEN-FINANCE-E-BOLETOS.md, seção 2.3, para o desenho completo. Este endpoint só
 * cria o Connect Token que o frontend usa para abrir o widget "Pluggy Connect" (hospedado
 * pela própria Pluggy) e o usuário autorizar a conexão de um banco. Os próximos passos
 * (listar contas/transações de um item conectado e importar como Entry) entram depois
 * que o widget estiver integrado no frontend.
 */
@RestController
@RequestMapping("/api/openfinance")
public class OpenFinanceController {

    private final PluggyClient pluggyClient;
    private final CurrentUserProvider currentUserProvider;

    public OpenFinanceController(PluggyClient pluggyClient, CurrentUserProvider currentUserProvider) {
        this.pluggyClient = pluggyClient;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping("/connect-token")
    public ConnectTokenResponse createConnectToken() {
        AppUser user = currentUserProvider.getCurrentUser();
        String accessToken = pluggyClient.createConnectToken(user.getId().toString());
        return new ConnectTokenResponse(accessToken);
    }
}
