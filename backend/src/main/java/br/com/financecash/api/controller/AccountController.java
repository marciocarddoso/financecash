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

    @GetMapping
    public List<AccountDTO> list() {
        return accountService.listActive();
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
}
