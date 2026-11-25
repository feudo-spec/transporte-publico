package com.transporte.auth;

import com.transporte.auth.messaging.UsuarioDesactivadoListener;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthServiceApplicationTests {

    private static final String PASSWORD = "MiClave#2026";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private UsuarioDesactivadoListener usuarioDesactivadoListener;

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    @Test
    void registroCreaLaCuentaYPublicaElEvento() throws Exception {
        String idUsuario = registrar("ana@mail.com");

        ArgumentCaptor<Message> mensaje = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(eq("transporte.eventos"), eq("usuario.registrado"), mensaje.capture());

        JsonNode evento = objectMapper.readTree(mensaje.getValue().getBody());
        assertThat(evento.path("tipo").asString()).isEqualTo("UsuarioRegistrado");
        assertThat(evento.path("version").asInt()).isEqualTo(1);
        assertThat(evento.path("eventId").asString()).isNotBlank();
        assertThat(evento.path("datos").path("idUsuario").asString()).isEqualTo(idUsuario);
        assertThat(evento.path("datos").path("cedula").asString()).isEqualTo("8-123-456");
        assertThat(evento.path("datos").path("fechaNacimiento").asString()).isEqualTo("2000-05-17");
        assertThat(evento.toString()).doesNotContain(PASSWORD);
    }

    @Test
    void registroConCorreoRepetidoDevuelve409() throws Exception {
        registrar("repetido@mail.com");

        mockMvc.perform(post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRegistro("REPETIDO@mail.com")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CORREO_YA_REGISTRADO"))
                .andExpect(jsonPath("$.ruta").value("/api/auth/registro"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void registroConDatosInvalidosDevuelve400() throws Exception {
        String json = """
                {"correo": "no-es-correo", "password": "corta", "cedula": "", "nombres": "Ana", "apellidos": "Pérez"}
                """;
        mockMvc.perform(post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
    }

    @Test
    void loginDevuelveTokensConLosClaimsDelContrato() throws Exception {
        String idUsuario = registrar("login@mail.com");

        JsonNode respuesta = login("login@mail.com", PASSWORD);
        assertThat(respuesta.path("tipo").asString()).isEqualTo("Bearer");
        assertThat(respuesta.path("expiraEn").asLong()).isEqualTo(900);
        assertThat(respuesta.path("refreshToken").asString()).isNotBlank();

        Jwt jwt = jwtDecoder.decode(respuesta.path("accessToken").asString());
        assertThat(jwt.getSubject()).isEqualTo(idUsuario);
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("auth-service");
        assertThat(jwt.getClaimAsString("correo")).isEqualTo("login@mail.com");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("USUARIO");
        assertThat(jwt.getHeaders().get("alg")).isEqualTo("HS256");
    }

    @Test
    void loginConPasswordIncorrectaDevuelve401() throws Exception {
        registrar("mala@mail.com");

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(jsonLogin("mala@mail.com", "OtraClave#1")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(jsonLogin("noexiste@mail.com", PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"));
    }

    @Test
    void refreshRotaElTokenYElAnteriorDejaDeServir() throws Exception {
        registrar("refresh@mail.com");
        String refreshToken = login("refresh@mail.com", PASSWORD).path("refreshToken").asString();

        MvcResult resultado = mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRefresh(refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andReturn();
        String nuevo = objectMapper.readTree(resultado.getResponse().getContentAsString())
                .path("refreshToken").asString();
        assertThat(nuevo).isNotEqualTo(refreshToken);

        mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRefresh(refreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("REFRESH_TOKEN_INVALIDO"));
    }

    @Test
    void logoutRevocaElRefreshToken() throws Exception {
        registrar("logout@mail.com");
        JsonNode tokens = login("logout@mail.com", PASSWORD);
        String accessToken = tokens.path("accessToken").asString();
        String refreshToken = tokens.path("refreshToken").asString();

        mockMvc.perform(post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + accessToken)
                        .content(jsonRefresh(refreshToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRefresh(refreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("REFRESH_TOKEN_INVALIDO"));
    }

    @Test
    void logoutSinTokenDevuelve401ConElFormatoDeError() throws Exception {
        mockMvc.perform(post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRefresh("cualquiera")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"))
                .andExpect(jsonPath("$.ruta").value("/api/auth/logout"));

        mockMvc.perform(post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer token.falso.xyz")
                        .content(jsonRefresh("cualquiera")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    @Test
    void usuarioDesactivadoNoPuedeIniciarSesionNiRefrescar() throws Exception {
        String idUsuario = registrar("baja@mail.com");
        String refreshToken = login("baja@mail.com", PASSWORD).path("refreshToken").asString();

        String evento = """
                {"eventId": "7c1d2e3f-0000-0000-0000-000000000001", "tipo": "UsuarioDesactivado", "version": 1,
                 "ocurridoEn": "2026-11-15T12:30:00Z",
                 "datos": {"idUsuario": "%s", "desactivadoEn": "2026-11-15T12:30:00Z"}}
                """.formatted(idUsuario);
        Message mensaje = MessageBuilder.withBody(evento.getBytes(StandardCharsets.UTF_8)).build();
        usuarioDesactivadoListener.recibir(mensaje);
        usuarioDesactivadoListener.recibir(mensaje);

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(jsonLogin("baja@mail.com", PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("USUARIO_DESACTIVADO"));

        mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRefresh(refreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("REFRESH_TOKEN_INVALIDO"));
    }

    @Test
    void clientCredentialsEntregaTokenConRolValidador() throws Exception {
        MvcResult resultado = mockMvc.perform(post("/api/auth/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials")
                        .param("client_id", "simulador-validadores")
                        .param("client_secret", "secreto-validador"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").value(900))
                .andReturn();

        String token = objectMapper.readTree(resultado.getResponse().getContentAsString())
                .path("access_token").asString();
        Jwt jwt = jwtDecoder.decode(token);
        assertThat(jwt.getSubject()).isEqualTo("simulador-validadores");
        assertThat(jwt.getClaimAsStringList("roles")).isEqualTo(List.of("VALIDADOR"));

        mockMvc.perform(post("/api/auth/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials")
                        .param("client_id", "simulador-validadores")
                        .param("client_secret", "incorrecto"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CLIENTE_INVALIDO"));
    }

    @Test
    void passwordGrantEntregaAccessYRefreshToken() throws Exception {
        registrar("oauth@mail.com");

        MvcResult resultado = mockMvc.perform(post("/api/auth/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "password")
                        .param("username", "oauth@mail.com")
                        .param("password", PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").value(900))
                .andExpect(jsonPath("$.refresh_token").isNotEmpty())
                .andReturn();

        String token = objectMapper.readTree(resultado.getResponse().getContentAsString())
                .path("access_token").asString();
        assertThat(jwtDecoder.decode(token).getClaimAsStringList("roles")).containsExactly("USUARIO");

        mockMvc.perform(post("/api/auth/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "password")
                        .param("username", "oauth@mail.com")
                        .param("password", "incorrecta"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"));
    }

    @Test
    void oauth2TokenRechazaGrantTypeDesconocido() throws Exception {
        mockMvc.perform(post("/api/auth/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "implicit"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
    }

    @Test
    void administradorInicialTieneRolAdministrador() throws Exception {
        JsonNode respuesta = login("admin@transporte.pa", "Admin#2026");
        Jwt jwt = jwtDecoder.decode(respuesta.path("accessToken").asString());
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("ADMINISTRADOR");
    }

    @Test
    void healthEsPublico() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    private String registrar(String correo) throws Exception {
        MvcResult resultado = mockMvc.perform(post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRegistro(correo)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(resultado.getResponse().getContentAsString()).path("idUsuario").asString();
    }

    private JsonNode login(String correo, String password) throws Exception {
        MvcResult resultado = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(jsonLogin(correo, password)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(resultado.getResponse().getContentAsString());
    }

    private String jsonRegistro(String correo) {
        return """
                {"correo": "%s", "password": "%s", "cedula": "8-123-456",
                 "nombres": "Ana", "apellidos": "Pérez", "fechaNacimiento": "2000-05-17",
                 "telefono": "6000-0000"}
                """.formatted(correo, PASSWORD);
    }

    private String jsonLogin(String correo, String password) {
        return """
                {"correo": "%s", "password": "%s"}
                """.formatted(correo, password);
    }

    private String jsonRefresh(String refreshToken) {
        return """
                {"refreshToken": "%s"}
                """.formatted(refreshToken);
    }
}
