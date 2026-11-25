package com.transporte.auth.messaging;

import com.transporte.auth.config.RabbitConfig;
import com.transporte.auth.service.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

@Component
public class UsuarioDesactivadoListener {

    private static final Logger log = LoggerFactory.getLogger(UsuarioDesactivadoListener.class);

    private final AuthService authService;
    private final ObjectMapper objectMapper;

    public UsuarioDesactivadoListener(AuthService authService, ObjectMapper objectMapper) {
        this.authService = authService;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = RabbitConfig.COLA_USUARIO_DESACTIVADO)
    public void recibir(Message mensaje) {
        JsonNode evento = objectMapper.readTree(mensaje.getBody());
        JsonNode idUsuario = evento.path("datos").path("idUsuario");
        if (!idUsuario.isString()) {
            log.warn("Evento UsuarioDesactivado sin datos.idUsuario, se descarta");
            return;
        }
        // si llega repetido no pasa nada
        authService.desactivarUsuario(UUID.fromString(idUsuario.asString()));
    }
}
