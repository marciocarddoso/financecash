package br.com.financecash.api.controller;

import br.com.financecash.application.dto.EntryCreateRequest;
import br.com.financecash.application.dto.EntryDTO;
import br.com.financecash.application.service.EntryService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/entries")
public class EntryController {

    private final EntryService entryService;

    public EntryController(EntryService entryService) {
        this.entryService = entryService;
    }

    @GetMapping
    public List<EntryDTO> list(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return entryService.listBetween(from, to);
    }

    @PostMapping
    public ResponseEntity<EntryDTO> create(@Valid @RequestBody EntryCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(entryService.create(request));
    }

    @PatchMapping("/{id}/pay")
    public EntryDTO markAsPaid(
            @PathVariable UUID id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate paymentDate) {
        return entryService.markAsPaid(id, paymentDate);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        entryService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
