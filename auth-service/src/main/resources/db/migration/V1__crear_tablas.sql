-- Tablas de auth_db segun el modelo de datos de la Tarea 2

CREATE TABLE credencial (
    id_credencial     BIGSERIAL PRIMARY KEY,
    id_usuario        UUID         NOT NULL UNIQUE,
    correo            VARCHAR(120) NOT NULL UNIQUE,
    password_hash     VARCHAR(255) NOT NULL,
    estado            VARCHAR(15)  NOT NULL,
    intentos_fallidos SMALLINT     NOT NULL DEFAULT 0,
    ultimo_acceso     TIMESTAMP WITH TIME ZONE,
    fecha_creacion    TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE rol (
    id_rol      SERIAL PRIMARY KEY,
    nombre      VARCHAR(40) NOT NULL UNIQUE,
    descripcion VARCHAR(200)
);

CREATE TABLE permiso (
    id_permiso  SERIAL PRIMARY KEY,
    codigo      VARCHAR(60) NOT NULL UNIQUE,
    descripcion VARCHAR(200)
);

CREATE TABLE credencial_rol (
    id_credencial    BIGINT NOT NULL REFERENCES credencial (id_credencial),
    id_rol           INT    NOT NULL REFERENCES rol (id_rol),
    fecha_asignacion TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id_credencial, id_rol)
);

CREATE TABLE rol_permiso (
    id_rol     INT NOT NULL REFERENCES rol (id_rol),
    id_permiso INT NOT NULL REFERENCES permiso (id_permiso),
    PRIMARY KEY (id_rol, id_permiso)
);

CREATE TABLE refresh_token (
    id_token      BIGSERIAL PRIMARY KEY,
    id_credencial BIGINT       NOT NULL REFERENCES credencial (id_credencial),
    token_hash    VARCHAR(255) NOT NULL UNIQUE,
    expira_en     TIMESTAMP WITH TIME ZONE NOT NULL,
    revocado      BOOLEAN      NOT NULL DEFAULT FALSE
);
