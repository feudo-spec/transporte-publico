package com.transporte.auth.repository;

import com.transporte.auth.entity.Credencial;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CredencialRepository extends JpaRepository<Credencial, Long> {

    Optional<Credencial> findByCorreo(String correo);

    Optional<Credencial> findByIdUsuario(UUID idUsuario);

    boolean existsByCorreo(String correo);
}
