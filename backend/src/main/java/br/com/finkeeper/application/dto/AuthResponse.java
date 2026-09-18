package br.com.finkeeper.application.dto;

public record AuthResponse(String token, String tokenType, String name, String email) {
    public static AuthResponse bearer(String token, String name, String email) {
        return new AuthResponse(token, "Bearer", name, email);
    }
}
