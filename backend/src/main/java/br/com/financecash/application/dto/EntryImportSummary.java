package br.com.financecash.application.dto;

import java.util.List;

/**
 * Resultado de uma importação em lote via {@code POST /api/entries/import-csv}.
 *
 * @param imported   quantidade de lançamentos efetivamente criados.
 * @param duplicates quantidade de linhas puladas por já existir um lançamento igual
 *                    (mesma descrição, vencimento e valor) para o usuário — permite
 *                    reprocessar o mesmo arquivo sem duplicar dados.
 * @param errors      linhas que não puderam ser importadas, com o motivo de cada uma.
 */
public record EntryImportSummary(int imported, int duplicates, List<EntryImportRowError> errors) {
}
