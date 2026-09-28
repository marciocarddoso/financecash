package br.com.financecash.api.controller;

import br.com.financecash.application.dto.BankAccountInfoDTO;
import br.com.financecash.application.dto.BankConnectionCreateRequest;
import br.com.financecash.application.dto.BankConnectionDTO;
import br.com.financecash.application.dto.ConnectTokenResponse;
import br.com.financecash.application.service.BankConnectionService;
import br.com.financecash.domain.model.AppUser;
import br.com.financecash.openfinance.PluggyClient;
import br.com.financecash.security.CurrentUserProvider;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Integração com Open Finance via Pluggy — ver docs/OPEN-FINANCE-E-BOLETOS.md, seção 2.3,
 * para o desenho completo. Fluxo: (1) frontend chama /connect-token e abre o widget
 * "Pluggy Connect" hospedado pela própria Pluggy; (2) quando o usuário autoriza um banco,
 * o widget dispara onSuccess com um itemId, que o frontend envia para /connections;
 * (3) /connections aparece na tela para o usuário ver quais bancos já conectou.
 *
 * Os próximos passos (listar contas/transações de um item e importar como Entry) entram
 * depois que este fluxo estiver validado ponta a ponta com contas reais.
 */
@RestController
@RequestMapping("/api/openfinance")
public class OpenFinanceController {

    private final PluggyClient pluggyClient;
    private final BankConnectionService bankConnectionService;
    private final CurrentUserProvider currentUserProvider;

    public OpenFinanceController(
            PluggyClient pluggyClient,
            BankConnectionService bankConnectionService,
            CurrentUserProvider currentUserProvider) {
        this.pluggyClient = pluggyClient;
        this.bankConnectionService = bankConnectionService;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping("/connect-token")
    public ConnectTokenResponse createConnectToken() {
        AppUser user = currentUserProvider.getCurrentUser();
        String accessToken = pluggyClient.createConnectToken(user.getId().toString());
        return new ConnectTokenResponse(accessToken);
    }

    @GetMapping("/connections")
    public List<BankConnectionDTO> listConnections() {
        return bankConnectionService.list();
    }

    @GetMapping("/connections/{id}/accounts")
    public List<BankAccountInfoDTO> listConnectionAccounts(@PathVariable UUID id) {
        return bankConnectionService.listAccounts(id);
    }

    @PostMapping("/connections")
    public ResponseEntity<BankConnectionDTO> saveConnection(@Valid @RequestBody BankConnectionCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bankConnectionService.save(request));
    }
}
