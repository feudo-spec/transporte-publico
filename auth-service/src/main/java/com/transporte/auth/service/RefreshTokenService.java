package com.transporte.auth.service;

import com.transporte.auth.entity.Credencial;
import com.transporte.auth.entity.RefreshToken;
import com.transporte.auth.repository.RefreshTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final Duration vigencia;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository,
                               @Value("${jwt.refresh-dias}") long dias) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.vigencia = Duration.ofDays(dias);
    }

    public String crear(Credencial credencial) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        refreshTokenRepository.save(new RefreshToken(credencial, hash(token), Instant.now().plus(vigencia)));
        return token;
    }

    public Optional<RefreshToken> buscar(String token) {
        return refreshTokenRepository.findByTokenHash(hash(token));
    }

    public void revocarTodos(Credencial credencial) {
        refreshTokenRepository.revocarTodos(credencial);
    }

    private String hash(String token) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha256.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
