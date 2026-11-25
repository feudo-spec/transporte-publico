package com.transporte.auth.messaging;

import java.time.Instant;
import java.util.UUID;

public record Evento<T>(UUID eventId, String tipo, int version, Instant ocurridoEn, T datos) {

    public static <T> Evento<T> nuevo(String tipo, T datos) {
        return new Evento<>(UUID.randomUUID(), tipo, 1, Instant.now(), datos);
    }
}
