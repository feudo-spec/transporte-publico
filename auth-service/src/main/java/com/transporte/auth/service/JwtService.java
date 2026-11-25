package com.transporte.auth.service;

import com.transporte.auth.entity.Credencial;
import com.transporte.auth.entity.Rol;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class JwtService {

    private static final String EMISOR = "auth-service";

    private final JwtEncoder jwtEncoder;
    private final Duration vigencia;

    public JwtService(JwtEncoder jwtEncoder, @Value("${jwt.access-minutos}") long minutos) {
        this.jwtEncoder = jwtEncoder;
        this.vigencia = Duration.ofMinutes(minutos);
    }

    public String generarParaUsuario(Credencial credencial) {
        List<String> roles = credencial.getRoles().stream().map(Rol::getNombre).sorted().toList();
        JwtClaimsSet.Builder claims = claimsBase(credencial.getIdUsuario().toString(), roles)
                .claim("correo", credencial.getCorreo());
        return firmar(claims.build());
    }

    public String generarParaCliente(String clientId) {
        return firmar(claimsBase(clientId, List.of(Rol.VALIDADOR)).build());
    }

    public long getVigenciaSegundos() {
        return vigencia.toSeconds();
    }

    private JwtClaimsSet.Builder claimsBase(String sujeto, List<String> roles) {
        Instant ahora = Instant.now();
        return JwtClaimsSet.builder()
                .issuer(EMISOR)
                .subject(sujeto)
                .issuedAt(ahora)
                .expiresAt(ahora.plus(vigencia))
                .claim("roles", roles);
    }

    private String firmar(JwtClaimsSet claims) {
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
