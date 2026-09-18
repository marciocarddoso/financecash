package br.com.financecash.api.controller;

import br.com.financecash.application.dto.InstallmentPlanCreateRequest;
import br.com.financecash.application.dto.InstallmentPlanDTO;
import br.com.financecash.application.service.InstallmentPlanService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/installment-plans")
public class InstallmentPlanController {

    private final InstallmentPlanService installmentPlanService;

    public InstallmentPlanController(InstallmentPlanService installmentPlanService) {
        this.installmentPlanService = installmentPlanService;
    }

    @GetMapping
    public List<InstallmentPlanDTO> list() {
        return installmentPlanService.listForCurrentUser();
    }

    /** Cria a compra parcelada e já gera todos os lançamentos das parcelas futuras. */
    @PostMapping
    public ResponseEntity<InstallmentPlanDTO> create(@Valid @RequestBody InstallmentPlanCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(installmentPlanService.create(request));
    }
}
