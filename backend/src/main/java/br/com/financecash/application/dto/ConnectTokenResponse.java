package br.com.financecash.application.dto;

/** Resposta de POST /api/openfinance/connect-token — o frontend usa accessToken para abrir o widget Pluggy Connect. */
public record ConnectTokenResponse(String accessToken) {
}
