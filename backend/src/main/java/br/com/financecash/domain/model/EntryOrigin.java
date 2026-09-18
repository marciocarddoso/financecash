package br.com.financecash.domain.model;

/**
 * De onde o lançamento veio. Existe desde a Fase 0 para que os futuros importadores
 * (boleto, fatura de cartão, extrato PIX — ver docs/ROADMAP.md) só precisem gravar
 * Entry com a origem correta, sem exigir migração de schema.
 */
public enum EntryOrigin {
    MANUAL,
    RECORRENCIA,
    PARCELAMENTO,
    IMPORTADO_BOLETO,
    IMPORTADO_CARTAO
}
