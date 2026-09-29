package br.com.financecash.api.controller;

import br.com.financecash.application.dto.ConfirmInvoicePaidRequest;
import br.com.financecash.application.dto.EntryBatchDeleteRequest;
import br.com.financecash.application.dto.EntryBatchOperationResult;
import br.com.financecash.application.dto.EntryBatchPayRequest;
import br.com.financecash.application.dto.EntryCreateRequest;
import br.com.financecash.application.dto.EntryDTO;
import br.com.financecash.application.dto.EntryPageDTO;
import br.com.financecash.application.dto.PendingInvoiceConfirmationDTO;
import br.com.financecash.application.dto.TotalsRegime;
import br.com.financecash.application.service.EntryService;
import br.com.financecash.domain.model.EntryOrigin;
import br.com.financecash.domain.model.EntryStatus;
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

    /**
     * Busca paginada com filtros — usada pela tela de Lançamentos (filtros de período,
     * categoria, status, origem, cartão/banco e descrição "contém"). Todos os filtros além do
     * período são opcionais. {@code regime} controla como o totalizador (não a lista, que
     * continua sempre por dueDate) soma despesa/receita — ver TotalsRegime; sem informar, usa
     * COMPETENCIA (comportamento original). {@code bankName} filtra por todos os cartões de um
     * banco de uma vez (ex. "Bradesco"), em vez de um cartão específico por vez — a pedido do
     * Marcio (01/10, quinta rodada).
     */
    @GetMapping("/search")
    public EntryPageDTO search(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) EntryStatus status,
            @RequestParam(required = false) EntryOrigin origin,
            @RequestParam(required = false) UUID creditCardId,
            @RequestParam(required = false) String bankName,
            @RequestParam(required = false) String description,
            @RequestParam(required = false, defaultValue = "COMPETENCIA") TotalsRegime regime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return entryService.search(from, to, categoryId, status, origin, creditCardId, bankName, description, regime, page, size);
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

    /** Desfaz um "marcar pago" feito sem querer — volta o lançamento pra PENDENTE. */
    @PatchMapping("/{id}/unpay")
    public EntryDTO markAsPending(@PathVariable UUID id) {
        return entryService.markAsPending(id);
    }

    /**
     * Marca manualmente um lançamento como "fora do total" — pra repasse/empréstimo pessoal que
     * nenhum sinal do banco identifica sozinho (ver EntryService.excludeFromTotals).
     */
    @PatchMapping("/{id}/exclude-from-totals")
    public EntryDTO excludeFromTotals(@PathVariable UUID id) {
        return entryService.excludeFromTotals(id);
    }

    /** Desfaz uma exclusão manual do totalizador feita sem querer. */
    @PatchMapping("/{id}/include-in-totals")
    public EntryDTO includeInTotals(@PathVariable UUID id) {
        return entryService.includeInTotals(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        entryService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** Edição em lote: marca vários lançamentos como pagos numa chamada só. */
    @PatchMapping("/batch-pay")
    public EntryBatchOperationResult batchMarkAsPaid(@Valid @RequestBody EntryBatchPayRequest request) {
        return entryService.batchMarkAsPaid(request.ids(), request.paymentDate());
    }

    /** Edição em lote: exclui vários lançamentos numa chamada só. */
    @PostMapping("/batch-delete")
    public EntryBatchOperationResult batchDelete(@Valid @RequestBody EntryBatchDeleteRequest request) {
        return entryService.batchDelete(request.ids());
    }

    /**
     * Faturas de cartão fechadas (vencimento no passado) que ainda têm compras PENDENTE — o
     * aviso "fatura tal venceu, já foi paga?" do dashboard. Sem referenceDate, usa hoje.
     */
    @GetMapping("/pending-invoice-confirmations")
    public List<PendingInvoiceConfirmationDTO> pendingInvoiceConfirmations(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate referenceDate) {
        return entryService.getPendingInvoiceConfirmations(referenceDate != null ? referenceDate : LocalDate.now());
    }

    /** Confirma que uma fatura de cartão inteira foi paga — ver EntryService.confirmInvoicePaid. */
    @PostMapping("/confirm-invoice-paid")
    public EntryBatchOperationResult confirmInvoicePaid(@Valid @RequestBody ConfirmInvoicePaidRequest request) {
        return entryService.confirmInvoicePaid(request.creditCardId(), request.dueDate(), request.paymentDate());
    }
}
