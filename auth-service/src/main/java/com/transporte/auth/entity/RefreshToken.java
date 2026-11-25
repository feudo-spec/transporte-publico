package com.transporte.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "refresh_token")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_token")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_credencial")
    private Credencial credencial;

    // se guarda el hash, no el token
    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "expira_en", nullable = false)
    private Instant expiraEn;

    @Column(nullable = false)
    private boolean revocado;

    protected RefreshToken() {
    }

    public RefreshToken(Credencial credencial, String tokenHash, Instant expiraEn) {
        this.credencial = credencial;
        this.tokenHash = tokenHash;
        this.expiraEn = expiraEn;
        this.revocado = false;
    }

    public boolean esValido() {
        return !revocado && expiraEn.isAfter(Instant.now());
    }

    public void revocar() {
        this.revocado = true;
    }

    public Long getId() { return id; }
    public Credencial getCredencial() { return credencial; }
    public String getTokenHash() { return tokenHash; }
    public Instant getExpiraEn() { return expiraEn; }
    public boolean isRevocado() { return revocado; }
}
