# auth-service

Servicio de autenticación y autorización de la Plataforma Digital de Transporte Público.
Gestiona las credenciales, los roles y emite los tokens JWT (HS256).

Contrato de API: [docs/api-contracts/auth-service.md](../docs/api-contracts/auth-service.md)

## Tecnologías

Java 25, Spring Boot 4.0, Spring Security, Spring Data JPA, PostgreSQL, Flyway, RabbitMQ, Maven.

## Endpoints

| Método y ruta | Acceso | Descripción |
|---|---|---|
| `POST /api/auth/registro` | público | Crea la credencial y publica `UsuarioRegistrado` |
| `POST /api/auth/login` | público | Devuelve access token y refresh token |
| `POST /api/auth/refresh` | público | Cambia un refresh token por un par nuevo |
| `POST /api/auth/logout` | autenticado | Revoca el refresh token |
| `POST /api/auth/oauth2/token` | público | Password y client credentials (simulador de validadores) |
| `GET /actuator/health` | público | Estado del servicio |
| `GET /actuator/prometheus` | público | Métricas para Prometheus |

## Cómo ejecutar en local

Requisitos: JDK 25 y Docker.

1. PostgreSQL (`auth_db`) y RabbitMQ con Docker. El archivo `docker-compose.dev.yml` levanta los dos:

   ```bash
   docker compose -f docker-compose.dev.yml up -d
   ```

   Crea la base `auth_db` vacía (usuario y contraseña `postgres`, puerto 5433); las tablas las crea
   Flyway al arrancar la aplicación. La consola de RabbitMQ queda en http://localhost:15672 (`guest` / `guest`).
   Para apagar: `docker compose -f docker-compose.dev.yml down` (agrega `-v` para borrar los datos).

   El contenedor se publica en el puerto 5433 del PC (no en el 5432) para no chocar con un
   PostgreSQL instalado en Windows.

2. Variables de entorno. Están descritas en `.env.example`. La única obligatoria es `JWT_SECRET`
   (mínimo 32 caracteres). En PowerShell:

   ```powershell
   $env:JWT_SECRET = "un-secreto-de-al-menos-32-caracteres-123"
   $env:DB_URL = "jdbc:postgresql://localhost:5433/auth_db"
   $env:DB_USER = "postgres"
   $env:DB_PASSWORD = "postgres"
   ```

3. Arrancar (queda en el puerto 8081):

   ```bash
   ./mvnw spring-boot:run        # en Windows: mvnw.cmd spring-boot:run
   ```

Las tablas y los roles iniciales se crean solos al arrancar (migraciones de Flyway en
`src/main/resources/db/migration`).

## Pruebas

```bash
./mvnw test
```

Las pruebas usan una base H2 en memoria y no necesitan RabbitMQ ni PostgreSQL.

## Probar a mano

```bash
curl -X POST http://localhost:8081/api/auth/registro -H "Content-Type: application/json" \
  -d '{"correo":"ana@mail.com","password":"MiClave#2026","cedula":"8-123-456","nombres":"Ana","apellidos":"Pérez","fechaNacimiento":"2000-05-17","telefono":"6000-0000"}'

curl -X POST http://localhost:8081/api/auth/login -H "Content-Type: application/json" \
  -d '{"correo":"ana@mail.com","password":"MiClave#2026"}'
```

## Docker

```bash
docker build -t auth-service .
docker run -p 8081:8081 --env-file .env auth-service
```

## Estructura

```
src/main/java/com/transporte/auth/
├── config/       Seguridad, JWT, RabbitMQ y administrador inicial
├── controller/   Endpoints REST
├── dto/          Objetos de entrada y salida
├── entity/       Entidades JPA (CREDENCIAL, ROL, PERMISO, REFRESH_TOKEN)
├── exception/    Manejo de errores con el formato del contrato
├── messaging/    Publicación y consumo de eventos
├── repository/   Acceso a datos
├── security/     Respuestas 401 y 403
└── service/      Lógica de negocio
```
