package com.transporte.auth.messaging;

import java.time.LocalDate;
import java.util.UUID;

public record UsuarioRegistradoDatos(UUID idUsuario, String correo, String cedula,
                                     String nombres, String apellidos, LocalDate fechaNacimiento,
                                     String telefono) {
}
