package com.transporte.auth.repository;

import com.transporte.auth.entity.Credencial;
import com.transporte.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("update RefreshToken t set t.revocado = true where t.credencial = :credencial and t.revocado = false")
    int revocarTodos(@Param("credencial") Credencial credencial);
}
