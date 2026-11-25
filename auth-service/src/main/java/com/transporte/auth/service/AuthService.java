package com.transporte.auth.service;

import com.transporte.auth.dto.LoginRequest;
import com.transporte.auth.dto.OAuthTokenResponse;
import com.transporte.auth.dto.RegistroRequest;
import com.transporte.auth.dto.RegistroResponse;
import com.transporte.auth.dto.TokenResponse;
import com.transporte.auth.entity.Credencial;
import com.transporte.auth.entity.RefreshToken;
import com.transporte.auth.entity.Rol;
import com.transporte.auth.exception.ApiException;
import com.transporte.auth.messaging.EventoPublisher;
import com.transporte.auth.messaging.UsuarioRegistradoDatos;
import com.transporte.auth.repository.CredencialRepository;
import com.transporte.auth.repository.RolRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final CredencialRepository credencialRepository;
    private final RolRepository rolRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final EventoPublisher eventoPublisher;
    private final String validadorClientId;
    private final String validadorClientSecret;

    public AuthService(CredencialRepository credencialRepository, RolRepository rolRepository,
                       PasswordEncoder passwordEncoder, JwtService jwtService,
                       RefreshTokenService refreshTokenService, EventoPublisher eventoPublisher,
                       @Value("${validador.client-id}") String validadorClientId,
                       @Value("${validador.client-secret}") String validadorClientSecret) {
        this.credencialRepository = credencialRepository;
        this.rolRepository = rolRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.eventoPublisher = eventoPublisher;
        this.validadorClientId = validadorClientId;
        this.validadorClientSecret = validadorClientSecret;
    }

    // si falla la publicacion del evento se hace rollback, para no dejar credenciales sin perfil
    @Transactional
    public RegistroResponse registrar(RegistroRequest request) {
        String correo = normalizar(request.correo());
        if (credencialRepository.existsByCorreo(correo)) {
            throw new ApiException(HttpStatus.CONFLICT, "CORREO_YA_REGISTRADO",
                    "Ya existe una cuenta con ese correo");
        }

        Rol rolUsuario = rolRepository.findByNombre(Rol.USUARIO)
                .orElseThrow(() -> new IllegalStateException("No existe el rol USUARIO"));

        UUID idUsuario = UUID.randomUUID();
        Credencial credencial = new Credencial(idUsuario, correo, passwordEncoder.encode(request.password()));
        credencial.getRoles().add(rolUsuario);
        credencialRepository.save(credencial);

        eventoPublisher.publicarUsuarioRegistrado(new UsuarioRegistradoDatos(
                idUsuario, correo, request.cedula().trim(), request.nombres().trim(),
                request.apellidos().trim(), request.fechaNacimiento(), request.telefono()));

        return new RegistroResponse(idUsuario);
    }

    // noRollbackFor para que se guarde el contador de intentos aunque el login falle
    @Transactional(noRollbackFor = ApiException.class)
    public TokenResponse login(LoginRequest request) {
        Optional<Credencial> encontrada = credencialRepository.findByCorreo(normalizar(request.correo()));

        // mismo error si el correo no existe o la clave esta mal
        if (encontrada.isEmpty()) {
            throw credencialesInvalidas();
        }
        Credencial credencial = encontrada.get();
        if (!passwordEncoder.matches(request.password(), credencial.getPasswordHash())) {
            credencial.registrarIntentoFallido();
            throw credencialesInvalidas();
        }
        if (!credencial.estaActiva()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "USUARIO_DESACTIVADO", "La cuenta está desactivada");
        }

        credencial.registrarAccesoExitoso();
        return emitirTokens(credencial);
    }

    // el refresh token usado se revoca, solo sirve una vez
    @Transactional
    public TokenResponse refrescar(String refreshToken) {
        RefreshToken actual = refreshTokenService.buscar(refreshToken)
                .filter(RefreshToken::esValido)
                .filter(token -> token.getCredencial().estaActiva())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_INVALIDO",
                        "El refresh token es inválido, está vencido o fue revocado"));

        actual.revocar();
        return emitirTokens(actual.getCredencial());
    }

    @Transactional
    public void logout(String refreshToken, String idUsuarioAutenticado) {
        refreshTokenService.buscar(refreshToken)
                .filter(token -> token.getCredencial().getIdUsuario().toString().equals(idUsuarioAutenticado))
                .ifPresent(RefreshToken::revocar);
    }

    public OAuthTokenResponse tokenClientCredentials(String grantType, String clientId, String clientSecret) {
        if (!"client_credentials".equals(grantType)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDACION",
                    "grant_type no soportado, use client_credentials");
        }
        boolean clienteValido = !validadorClientSecret.isBlank()
                && iguales(validadorClientId, clientId)
                && iguales(validadorClientSecret, clientSecret);
        if (!clienteValido) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "CLIENTE_INVALIDO", "Cliente o secreto incorrectos");
        }
        return new OAuthTokenResponse(jwtService.generarParaCliente(clientId), "Bearer",
                jwtService.getVigenciaSegundos(), null);
    }

    @Transactional
    public void desactivarUsuario(UUID idUsuario) {
        Optional<Credencial> encontrada = credencialRepository.findByIdUsuario(idUsuario);
        if (encontrada.isEmpty()) {
            log.warn("UsuarioDesactivado: no existe credencial para el usuario {}", idUsuario);
            return;
        }
        Credencial credencial = encontrada.get();
        credencial.desactivar();
        refreshTokenService.revocarTodos(credencial);
        log.info("Credencial del usuario {} desactivada", idUsuario);
    }

    private TokenResponse emitirTokens(Credencial credencial) {
        String accessToken = jwtService.generarParaUsuario(credencial);
        String refreshToken = refreshTokenService.crear(credencial);
        return TokenResponse.bearer(accessToken, refreshToken, jwtService.getVigenciaSegundos());
    }

    private ApiException credencialesInvalidas() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "CREDENCIALES_INVALIDAS", "Correo o contraseña incorrectos");
    }

    private String normalizar(String correo) {
        return correo.trim().toLowerCase();
    }

    // comparacion en tiempo constante
    private boolean iguales(String esperado, String recibido) {
        return MessageDigest.isEqual(esperado.getBytes(StandardCharsets.UTF_8),
                recibido.getBytes(StandardCharsets.UTF_8));
    }
}
