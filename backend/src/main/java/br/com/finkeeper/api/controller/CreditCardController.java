package br.com.finkeeper.api.controller;

import br.com.finkeeper.application.dto.CreditCardCreateRequest;
import br.com.finkeeper.application.dto.CreditCardDTO;
import br.com.finkeeper.application.service.CreditCardService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
}
