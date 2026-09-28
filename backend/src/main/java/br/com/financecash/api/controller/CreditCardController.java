package br.com.financecash.api.controller;

import br.com.financecash.application.dto.CreditCardCreateRequest;
import br.com.financecash.application.dto.CreditCardDTO;
import br.com.financecash.application.dto.CreditCardUpdateRequest;
import br.com.financecash.application.service.CreditCardService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/credit-cards")
public class CreditCardController {

    private final CreditCardService creditCardService;

    public CreditCardController(CreditCardService creditCardService) {
        this.creditCardService = creditCardService;
    }

    @GetMapping
    public List<CreditCardDTO> list() {
        return creditCardService.listActive();
    }

    @PostMapping
    public ResponseEntity<CreditCardDTO> create(@Valid @RequestBody CreditCardCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(creditCardService.create(request));
    }

    @PutMapping("/{id}")
    public CreditCardDTO update(@PathVariable UUID id, @Valid @RequestBody CreditCardUpdateRequest request) {
        return creditCardService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        creditCardService.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
