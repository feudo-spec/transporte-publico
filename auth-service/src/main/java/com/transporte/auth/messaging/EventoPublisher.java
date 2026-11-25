package com.transporte.auth.messaging;

import com.transporte.auth.config.RabbitConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

@Component
public class EventoPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventoPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public EventoPublisher(RabbitTemplate rabbitTemplate, ObjectMapper objectMapper) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    public void publicarUsuarioRegistrado(UsuarioRegistradoDatos datos) {
        publicar(RabbitConfig.RK_USUARIO_REGISTRADO, Evento.nuevo("UsuarioRegistrado", datos));
    }

    // se manda como JSON plano para que los otros servicios no dependan de estas clases
    private void publicar(String routingKey, Evento<?> evento) {
        String json = objectMapper.writeValueAsString(evento);
        Message mensaje = MessageBuilder.withBody(json.getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setContentEncoding(StandardCharsets.UTF_8.name())
                .setMessageId(evento.eventId().toString())
                .build();
        rabbitTemplate.send(RabbitConfig.EXCHANGE, routingKey, mensaje);
        log.info("Evento {} publicado (eventId={})", evento.tipo(), evento.eventId());
    }
}
