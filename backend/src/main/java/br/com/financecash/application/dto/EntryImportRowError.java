package br.com.financecash.application.dto;

/** Uma linha do CSV de importação que não pôde ser processada, com o motivo. */
public record EntryImportRowError(long line, String message) {
}
