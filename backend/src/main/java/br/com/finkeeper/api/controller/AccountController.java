package br.com.finkeeper.api.controller;

import br.com.finkeeper.application.dto.AccountCreateRequest;
import br.com.finkeeper.application.dto.AccountDTO;
import br.com.finkeeper.application.dto.BalanceSnapshotCreateRequest;
import br.com.finkeeper.application.service.AccountService;
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

    /** Registra o saldo real informado pelo usuário (consolidação manual com o banco/CDI). */
    @PostMapping("/{id}/balance-snapshots")
    public ResponseEntity<Void> registerBalance(@PathVariable UUID id, @Valid @RequestBody BalanceSnapshotCreateRequest request) {
        accountService.registerBalanceSnapshot(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
