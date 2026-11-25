package com.transporte.auth.controller;

import com.transporte.auth.dto.LoginRequest;
import com.transporte.auth.dto.OAuthTokenResponse;
import com.transporte.auth.dto.RefreshRequest;
import com.transporte.auth.dto.RegistroRequest;
import com.transporte.auth.dto.RegistroResponse;
import com.transporte.auth.dto.TokenResponse;
import com.transporte.auth.exception.ApiException;
import com.transporte.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/registro")
    @ResponseStatus(HttpStatus.CREATED)
    public RegistroResponse registro(@Valid @RequestBody RegistroRequest request) {
        return authService.registrar(request);
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refrescar(request.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshRequest request, @AuthenticationPrincipal Jwt jwt) {
        authService.logout(request.refreshToken(), jwt.getSubject());
    }

    @PostMapping(value = "/oauth2/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public OAuthTokenResponse token(@RequestParam("grant_type") String grantType,
                                    @RequestParam(value = "client_id", required = false) String clientId,
                                    @RequestParam(value = "client_secret", required = false) String clientSecret,
                                    @RequestParam(value = "username", required = false) String username,
                                    @RequestParam(value = "password", required = false) String password) {
        if ("password".equals(grantType)) {
            if (username == null || username.isBlank() || password == null || password.isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDACION", "username y password son obligatorios");
            }
            TokenResponse tokens = authService.login(new LoginRequest(username, password));
            return new OAuthTokenResponse(tokens.accessToken(), "Bearer", tokens.expiraEn(), tokens.refreshToken());
        }
        if ("client_credentials".equals(grantType)) {
            if (clientId == null || clientSecret == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDACION", "client_id y client_secret son obligatorios");
            }
            return authService.tokenClientCredentials(grantType, clientId, clientSecret);
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDACION",
                "grant_type no soportado, use password o client_credentials");
    }
}
