package br.com.finkeeper.api.controller;

import br.com.finkeeper.application.dto.InstallmentPlanCreateRequest;
import br.com.finkeeper.application.dto.InstallmentPlanDTO;
import br.com.finkeeper.application.service.InstallmentPlanService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/installment-plans")
public class InstallmentPlanController {

    private final InstallmentPlanService installmentPlanService;

    public InstallmentPlanController(InstallmentPlanService installmentPlanService) {
        this.installmentPlanService = installmentPlanService;
    }

    /** Cria a compra parcelada e já gera todos os lançamentos das parcelas futuras. */
    @PostMapping
    public ResponseEntity<InstallmentPlanDTO> create(@Valid @RequestBody InstallmentPlanCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(installmentPlanService.create(request));
    }
}
