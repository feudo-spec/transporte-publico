package com.transporte.auth.dto;

public record TokenResponse(String accessToken, String refreshToken, String tipo, long expiraEn) {

    public static TokenResponse bearer(String accessToken, String refreshToken, long expiraEn) {
        return new TokenResponse(accessToken, refreshToken, "Bearer", expiraEn);
    }
}
