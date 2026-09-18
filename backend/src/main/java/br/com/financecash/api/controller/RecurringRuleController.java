package br.com.financecash.api.controller;

import br.com.financecash.application.dto.RecurringRuleCreateRequest;
import br.com.financecash.application.dto.RecurringRuleDTO;
import br.com.financecash.application.service.RecurringEntryGenerationService;
import br.com.financecash.application.service.RecurringRuleService;
import br.com.financecash.security.CurrentUserProvider;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/recurring-rules")
@Validated
public class RecurringRuleController {

    private final RecurringRuleService recurringRuleService;
    private final RecurringEntryGenerationService generationService;
    private final CurrentUserProvider currentUserProvider;

    public RecurringRuleController(RecurringRuleService recurringRuleService,
                                    RecurringEntryGenerationService generationService,
                                    CurrentUserProvider currentUserProvider) {
        this.recurringRuleService = recurringRuleService;
        this.generationService = generationService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    public List<RecurringRuleDTO> list() {
        return recurringRuleService.listActive();
    }

    @PostMapping
    public ResponseEntity<RecurringRuleDTO> create(@Valid @RequestBody RecurringRuleCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(recurringRuleService.create(request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        recurringRuleService.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Dispara o provisionamento: gera os lançamentos futuros de todas as regras
     * ativas, a partir de hoje, para os próximos `monthsAhead` meses (padrão 12).
     * Idempotente — pode ser chamado quantas vezes quiser.
     */
    @PostMapping("/generate")
    public ResponseEntity<Map<String, Integer>> generate(@RequestParam(defaultValue = "12") @Positive int monthsAhead) {
        int created = generationService.generateForUser(currentUserProvider.getCurrentUser(), LocalDate.now(), monthsAhead);
        return ResponseEntity.ok(Map.of("lancamentosGerados", created));
    }
}
