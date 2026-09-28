package br.com.financecash.domain.model;

/**
 * Status simplificado de uma conexão bancária, derivado do campo {@code status}
 * do Item da Pluggy (ver PluggyClient.getItem). A Pluggy tem estados mais granulares
 * (ex.: WAITING_USER_ACTION para MFA pendente) que aqui caem em ERRO — o usuário
 * precisa voltar no "Meu Pluggy" ou reconectar para resolver, então não vale a
 * pena modelar cada estado transitório no FinanceCash.
 */
public enum BankConnectionStatus {
    ATIVA,
    EXPIRADA,
    ERRO
}
