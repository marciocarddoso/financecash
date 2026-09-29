package br.com.financecash.application.dto;

import br.com.financecash.domain.model.Entry;
import org.springframework.data.domain.Page;

import java.util.List;

/** Página de lançamentos retornada por GET /api/entries/search — ver EntryService.search. */
public record EntryPageDTO(
        List<EntryDTO> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        EntryTotalsDTO totals
) {
    public static EntryPageDTO from(Page<Entry> page, EntryTotalsDTO totals) {
        return new EntryPageDTO(
                page.getContent().stream().map(EntryDTO::from).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                totals
        );
    }
}
