package com.transporte.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "credencial")
public class Credencial {

    public static final String ACTIVO = "ACTIVO";
    public static final String DESACTIVADO = "DESACTIVADO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_credencial")
    private Long id;

    // id del usuario en usuarios-service
    @Column(name = "id_usuario", nullable = false, unique = true)
    private UUID idUsuario;

    @Column(nullable = false, unique = true, length = 120)
    private String correo;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false, length = 15)
    private String estado;

    @Column(name = "intentos_fallidos", nullable = false)
    private short intentosFallidos;

    @Column(name = "ultimo_acceso")
    private Instant ultimoAcceso;

    @Column(name = "fecha_creacion", nullable = false)
    private Instant fechaCreacion;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "credencial_rol",
            joinColumns = @JoinColumn(name = "id_credencial"),
            inverseJoinColumns = @JoinColumn(name = "id_rol"))
    private Set<Rol> roles = new HashSet<>();

    protected Credencial() {
    }

    public Credencial(UUID idUsuario, String correo, String passwordHash) {
        this.idUsuario = idUsuario;
        this.correo = correo;
        this.passwordHash = passwordHash;
        this.estado = ACTIVO;
        this.intentosFallidos = 0;
        this.fechaCreacion = Instant.now();
    }

    public boolean estaActiva() {
        return ACTIVO.equals(estado);
    }

    public void registrarAccesoExitoso() {
        this.intentosFallidos = 0;
        this.ultimoAcceso = Instant.now();
    }

    public void registrarIntentoFallido() {
        this.intentosFallidos++;
    }

    public void desactivar() {
        this.estado = DESACTIVADO;
    }

    public Long getId() { return id; }
    public UUID getIdUsuario() { return idUsuario; }
    public String getCorreo() { return correo; }
    public String getPasswordHash() { return passwordHash; }
    public String getEstado() { return estado; }
    public short getIntentosFallidos() { return intentosFallidos; }
    public Instant getUltimoAcceso() { return ultimoAcceso; }
    public Instant getFechaCreacion() { return fechaCreacion; }
    public Set<Rol> getRoles() { return roles; }
}
