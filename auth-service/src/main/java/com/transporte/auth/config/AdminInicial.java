package com.transporte.auth.config;

import com.transporte.auth.entity.Credencial;
import com.transporte.auth.entity.Rol;
import com.transporte.auth.repository.CredencialRepository;
import com.transporte.auth.repository.RolRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class AdminInicial implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminInicial.class);

    private final CredencialRepository credencialRepository;
    private final RolRepository rolRepository;
    private final PasswordEncoder passwordEncoder;
    private final String correo;
    private final String password;

    public AdminInicial(CredencialRepository credencialRepository, RolRepository rolRepository,
                        PasswordEncoder passwordEncoder,
                        @Value("${admin.correo:}") String correo,
                        @Value("${admin.password:}") String password) {
        this.credencialRepository = credencialRepository;
        this.rolRepository = rolRepository;
        this.passwordEncoder = passwordEncoder;
        this.correo = correo.trim().toLowerCase();
        this.password = password;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (correo.isEmpty() || password.isEmpty()) {
            return;
        }
        if (credencialRepository.existsByCorreo(correo)) {
            return;
        }
        Rol rolAdmin = rolRepository.findByNombre(Rol.ADMINISTRADOR)
                .orElseThrow(() -> new IllegalStateException("No existe el rol ADMINISTRADOR"));

        Credencial admin = new Credencial(UUID.randomUUID(), correo, passwordEncoder.encode(password));
        admin.getRoles().add(rolAdmin);
        credencialRepository.save(admin);
        log.info("Administrador inicial creado: {}", correo);
    }
}
