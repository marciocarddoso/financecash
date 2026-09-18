package br.com.financecash.application.dto;

import jakarta.validation.constraints.NotEmpty;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** paymentDate nulo usa a data de hoje (mesmo default de EntryService#markAsPaid). */
public record EntryBatchPayRequest(@NotEmpty List<UUID> ids, LocalDate paymentDate) {
}
