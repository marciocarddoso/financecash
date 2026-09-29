package br.com.financecash.api.controller;

import br.com.financecash.application.dto.AccountCreateRequest;
import br.com.financecash.application.dto.AccountDTO;
import br.com.financecash.application.dto.AccountUpdateRequest;
import br.com.financecash.application.dto.BalanceSnapshotCreateRequest;
import br.com.financecash.application.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    /**
     * Lista as contas do usuário. Por padrão só as ativas (usado nos seletores de conta ao
     * criar lançamento/parcelamento — não faz sentido oferecer uma conta desativada ali).
     * A tela Contas & Saldos passa includeInactive=true pra também mostrar as desativadas e
     * permitir reativá-las.
     */
    @GetMapping
    public List<AccountDTO> list(@RequestParam(name = "includeInactive", defaultValue = "false") boolean includeInactive) {
        return includeInactive ? accountService.listAll() : accountService.listActive();
    }

    @PostMapping
    public ResponseEntity<AccountDTO> create(@Valid @RequestBody AccountCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(accountService.create(request));
    }

    @PutMapping("/{id}")
    public AccountDTO update(@PathVariable UUID id, @Valid @RequestBody AccountUpdateRequest request) {
        return accountService.update(id, request);
    }

    /** Registra o saldo real informado pelo usuário (consolidação manual com o banco/CDI). */
    @PostMapping("/{id}/balance-snapshots")
    public ResponseEntity<Void> registerBalance(@PathVariable UUID id, @Valid @RequestBody BalanceSnapshotCreateRequest request) {
        accountService.registerBalanceSnapshot(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        accountService.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<Void> activate(@PathVariable UUID id) {
        accountService.activate(id);
        return ResponseEntity.noContent().build();
    }
}
