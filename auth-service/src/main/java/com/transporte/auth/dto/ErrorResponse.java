package com.transporte.auth.dto;

import java.time.Instant;

public record ErrorResponse(String codigo, String mensaje, Instant timestamp, String ruta) {

    public static ErrorResponse de(String codigo, String mensaje, String ruta) {
        return new ErrorResponse(codigo, mensaje, Instant.now(), ruta);
    }
}
