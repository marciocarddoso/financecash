package br.com.financecash.application.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record EntryBatchDeleteRequest(@NotEmpty List<UUID> ids) {
}
