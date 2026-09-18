package br.com.financecash.application.dto;

import java.util.List;
import java.util.UUID;

/**
 * @param affected quantos lançamentos foram de fato alterados/excluídos.
 * @param notFound  ids que não existiam ou não pertenciam ao usuário logado — por
 *                   segurança, tentar mexer em lançamento de outro usuário aparece
 *                   aqui como "não encontrado", nunca como erro de permissão.
 */
public record EntryBatchOperationResult(int affected, List<UUID> notFound) {
}
