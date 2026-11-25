# auth-service — Contrato de API

Servicio responsable de las credenciales, los roles y la emisión de tokens JWT. Base de datos: `auth_db`. Entidades: `CREDENCIAL`, `ROL`, `PERMISO`, `CREDENCIAL_ROL`, `ROL_PERMISO`, `REFRESH_TOKEN`.


## 1. Convenciones

- Todo el tráfico externo entra por la API Gateway (`/api/**`). Las rutas `/internal/**` solo existen dentro de la red de Docker y la Gateway **NO** las expone.
- Formato: JSON, UTF-8. Fechas en ISO-8601 (`2026-11-15T12:30:00Z`).
- Identificadores: UUID.
- Nombres de campos en JSON: `camelCase`.
- Cabecera de autenticación: `Authorization: Bearer <accessToken>`.

### Formato de error (todos los servicios)

```json
{
  "codigo": "CREDENCIALES_INVALIDAS",
  "mensaje": "Correo o contraseña incorrectos",
  "timestamp": "2026-11-15T12:30:00Z",
  "ruta": "/api/auth/login"
}
```

| HTTP | Uso |
|---|---|
| 400 | Datos inválidos (`VALIDACION`) |
| 401 | Sin token, token vencido o inválido |
| 403 | Token válido pero sin el rol necesario |
| 404 | Recurso no existe |
| 409 | Conflicto (correo ya registrado) |

## 2. JWT

- **Algoritmo:** HS256 (firma simétrica).
- **Secreto:** variable de entorno `JWT_SECRET`, de al menos 32 caracteres. Se define una sola vez en el `docker-compose.yml` y lo usan `auth-service` (para firmar) y los demás servicios y la Gateway (para validar). No se sube al repositorio.

**Access token** (vigencia 15 minutos):

```json
{
  "iss": "auth-service",
  "sub": "b3f1c9e2-5a41-4c7e-9d0a-1f2e3a4b5c6d",
  "correo": "ana@mail.com",
  "roles": ["USUARIO"],
  "iat": 1763209800,
  "exp": 1763210700
}
```

- `sub` = `idUsuario` (UUID).
- `roles`: `USUARIO`, `ADMINISTRADOR` o `VALIDADOR`. En Spring Security se mapean a `ROLE_<rol>`.
- El simulador de validadores usa client credentials y recibe un token con `roles: ["VALIDADOR"]` y `sub` = id del cliente.

**Refresh token** (vigencia 7 días): cadena opaca guardada en `REFRESH_TOKEN`, revocable.

## 3. Endpoints

### POST /api/auth/registro — público

```json
// Request
{
  "correo": "ana@mail.com",
  "password": "MiClave#2026",
  "cedula": "8-123-456",
  "nombres": "Ana",
  "apellidos": "Pérez",
  "fechaNacimiento": "2000-05-17",
  "telefono": "6000-0000"
}
// Response 201
{ "idUsuario": "b3f1c9e2-5a41-4c7e-9d0a-1f2e3a4b5c6d" }
```

`telefono` es opcional; los demás campos son obligatorios y `fechaNacimiento` (formato `yyyy-MM-dd`) debe ser anterior a hoy.

Errores: 400 `VALIDACION`, 409 `CORREO_YA_REGISTRADO`.
Efecto: crea la credencial con rol `USUARIO` (contraseña con BCrypt) y publica `UsuarioRegistrado`. Los datos personales viajan en el evento para que `usuarios-service` cree el perfil.

### POST /api/auth/login — público

```json
// Request
{ "correo": "ana@mail.com", "password": "MiClave#2026" }
// Response 200
{ "accessToken": "eyJ...", "refreshToken": "9f8e...", "tipo": "Bearer", "expiraEn": 900 }
```

Errores: 401 `CREDENCIALES_INVALIDAS`, 403 `USUARIO_DESACTIVADO`.

### POST /api/auth/refresh — público

```json
// Request
{ "refreshToken": "9f8e..." }
// Response 200: igual que login
```

Errores: 401 `REFRESH_TOKEN_INVALIDO` (vencido o revocado).

### POST /api/auth/logout — autenticado

```json
// Request
{ "refreshToken": "9f8e..." }
// Response 204
```

Efecto: revoca el refresh token.

### POST /api/auth/oauth2/token — password y client credentials

Formulario `application/x-www-form-urlencoded`. Según `grant_type`:

- `password` (usuarios): `grant_type=password&username=<correo>&password=<contraseña>`. Equivale a `/login` con formato OAuth2.
- `client_credentials` (VALIDADOR): `grant_type=client_credentials&client_id=...&client_secret=...`

```json
// Response 200 (password)
{ "access_token": "...", "token_type": "Bearer", "expires_in": 900, "refresh_token": "9f8e..." }
// Response 200 (client_credentials): no incluye refresh_token
{ "access_token": "...", "token_type": "Bearer", "expires_in": 900 }
```

Errores: 400 `VALIDACION` (falta un parámetro o `grant_type` no soportado), 401 `CREDENCIALES_INVALIDAS` (password), 401 `CLIENTE_INVALIDO` (client_credentials), 403 `USUARIO_DESACTIVADO` (password).

### Rutas públicas (no requieren token)

`/api/auth/registro`, `/api/auth/login`, `/api/auth/refresh`, `/api/auth/oauth2/token`.

### Errores generados por Spring Security

Además de los errores de cada endpoint, cualquier ruta protegida puede devolver, con el mismo formato de error:

- 401 `NO_AUTENTICADO`: falta el token, está vencido o la firma no es válida.
- 403 `ACCESO_DENEGADO`: el token es válido pero no tiene el rol necesario.
- 503 `SERVICIO_NO_DISPONIBLE`: RabbitMQ no está disponible y no se pudo publicar el evento (por ejemplo en el registro, que se revierte).

## 4. Eventos

- **Exchange:** `transporte.eventos` (tipo `topic`, durable).
- Los consumidores deben ser **idempotentes**: se puede recibir el mismo `eventId` dos veces.

### Sobre común

```json
{
  "eventId": "7c1d2e3f-…",
  "tipo": "UsuarioRegistrado",
  "version": 1,
  "ocurridoEn": "2026-11-15T12:30:00Z",
  "datos": { }
}
```

### UsuarioRegistrado — lo publica auth-service

Routing key: `usuario.registrado`. Consume: usuarios-service (cola `usuarios.usuario-registrado`).

```json
"datos": {
  "idUsuario": "b3f1c9e2-…", "correo": "ana@mail.com", "cedula": "8-123-456",
  "nombres": "Ana", "apellidos": "Pérez", "fechaNacimiento": "2000-05-17",
  "telefono": "6000-0000"
}
```

### UsuarioDesactivado — lo consume auth-service

Routing key: `usuario.desactivado`. Cola: `auth.usuario-desactivado`.

```json
"datos": { "idUsuario": "b3f1c9e2-…", "desactivadoEn": "2026-11-15T12:30:00Z" }
```

Efecto: bloquea la credencial (el login devuelve 403 `USUARIO_DESACTIVADO`) y revoca sus refresh tokens.
