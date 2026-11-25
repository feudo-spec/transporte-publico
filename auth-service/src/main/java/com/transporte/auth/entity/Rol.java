package com.transporte.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "rol")
public class Rol {

    public static final String USUARIO = "USUARIO";
    public static final String ADMINISTRADOR = "ADMINISTRADOR";
    public static final String VALIDADOR = "VALIDADOR";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_rol")
    private Integer id;

    @Column(nullable = false, unique = true, length = 40)
    private String nombre;

    @Column(length = 200)
    private String descripcion;

    @ManyToMany
    @JoinTable(name = "rol_permiso",
            joinColumns = @JoinColumn(name = "id_rol"),
            inverseJoinColumns = @JoinColumn(name = "id_permiso"))
    private Set<Permiso> permisos = new HashSet<>();

    public Integer getId() { return id; }
    public String getNombre() { return nombre; }
    public String getDescripcion() { return descripcion; }
    public Set<Permiso> getPermisos() { return permisos; }
}
