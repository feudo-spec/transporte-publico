package com.transporte.auth.exception;

import com.transporte.auth.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> manejarApi(ApiException ex, HttpServletRequest request) {
        return responder(ex.getStatus(), ex.getCodigo(), ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> manejarValidacion(MethodArgumentNotValidException ex,
                                                           HttpServletRequest request) {
        String mensaje = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getDefaultMessage())
                .sorted()
                .collect(Collectors.joining("; "));
        return responder(HttpStatus.BAD_REQUEST, "VALIDACION", mensaje, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> manejarJsonInvalido(HttpMessageNotReadableException ex,
                                                             HttpServletRequest request) {
        return responder(HttpStatus.BAD_REQUEST, "VALIDACION", "El cuerpo de la petición no es un JSON válido", request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> manejarParametroFaltante(MissingServletRequestParameterException ex,
                                                                  HttpServletRequest request) {
        return responder(HttpStatus.BAD_REQUEST, "VALIDACION",
                "Falta el parámetro " + ex.getParameterName(), request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> manejarTipoNoSoportado(HttpMediaTypeNotSupportedException ex,
                                                                HttpServletRequest request) {
        return responder(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "VALIDACION", "Content-Type no soportado", request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> manejarMetodoNoPermitido(HttpRequestMethodNotSupportedException ex,
                                                                  HttpServletRequest request) {
        return responder(HttpStatus.METHOD_NOT_ALLOWED, "METODO_NO_PERMITIDO", "Método HTTP no permitido", request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> manejarNoEncontrado(NoResourceFoundException ex,
                                                             HttpServletRequest request) {
        return responder(HttpStatus.NOT_FOUND, "NO_ENCONTRADO", "El recurso no existe", request);
    }

    // si RabbitMQ esta caido no se publica el evento y se revierte el registro
    @ExceptionHandler(AmqpException.class)
    public ResponseEntity<ErrorResponse> manejarMensajeria(AmqpException ex, HttpServletRequest request) {
        log.error("No se pudo publicar el evento en RabbitMQ", ex);
        return responder(HttpStatus.SERVICE_UNAVAILABLE, "SERVICIO_NO_DISPONIBLE",
                "El servicio no está disponible en este momento, intente más tarde", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> manejarInesperado(Exception ex, HttpServletRequest request) {
        log.error("Error inesperado en {}", request.getRequestURI(), ex);
        return responder(HttpStatus.INTERNAL_SERVER_ERROR, "ERROR_INTERNO", "Ocurrió un error inesperado", request);
    }

    private ResponseEntity<ErrorResponse> responder(HttpStatus status, String codigo, String mensaje,
                                                    HttpServletRequest request) {
        return ResponseEntity.status(status).body(ErrorResponse.de(codigo, mensaje, request.getRequestURI()));
    }
}
