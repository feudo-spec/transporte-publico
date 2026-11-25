package com.transporte.auth.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "transporte.eventos";

    public static final String RK_USUARIO_REGISTRADO = "usuario.registrado";
    public static final String RK_USUARIO_DESACTIVADO = "usuario.desactivado";

    public static final String COLA_USUARIO_DESACTIVADO = "auth.usuario-desactivado";

    @Bean
    public TopicExchange exchangeEventos() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue colaUsuarioDesactivado() {
        return new Queue(COLA_USUARIO_DESACTIVADO, true);
    }

    @Bean
    public Binding bindingUsuarioDesactivado(Queue colaUsuarioDesactivado, TopicExchange exchangeEventos) {
        return BindingBuilder.bind(colaUsuarioDesactivado).to(exchangeEventos).with(RK_USUARIO_DESACTIVADO);
    }
}
