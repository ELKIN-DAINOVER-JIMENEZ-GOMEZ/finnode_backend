# 🔐 auth-service — El Guardián de Identidades

> Único microservicio de FinNode responsable de la identidad del usuario. Emite y valida tokens JWT. Ningún otro servicio maneja contraseñas ni sesiones.

---

## 📋 Tabla de Contenidos

- [Responsabilidades](#-responsabilidades)
- [Endpoints](#-endpoints)
- [Estructura de Carpetas](#-estructura-de-carpetas)
- [Descripción de Cada Capa](#-descripción-de-cada-capa)
- [Modelo de Base de Datos](#-modelo-de-base-de-datos)
- [Evento Kafka Publicado](#-evento-kafka-publicado)
- [Dependencias](#-dependencias)
- [Variables de Entorno](#-variables-de-entorno)

---

## ✅ Responsabilidades

| # | Responsabilidad | Descripción |
|---|----------------|-------------|
| 1 | **Registro** | Recibe datos del nuevo usuario, valida, encripta la contraseña y persiste en PostgreSQL |
| 2 | **Login** | Valida credenciales y emite un par de tokens: `access_token` (corto) + `refresh_token` (largo) |
| 3 | **Refresh** | Renueva el `access_token` usando un `refresh_token` válido, sin pedir contraseña nuevamente |
| 4 | **Evento Kafka** | Al registrar un usuario exitosamente, publica `UserRegisteredEvent` para que `account-service` cree la cuenta bancaria automáticamente |

---

## 🌐 Endpoints

```
POST /auth/register   → Registro de nuevo usuario
POST /auth/login      → Login y emisión de JWT
POST /auth/refresh    → Renovación de access token
```

> Todos los endpoints de este servicio son **públicos** (no requieren JWT entrante). La seguridad perimetral la aplica el `api-gateway`.

---

## 📁 Estructura de Carpetas

```
auth-service/
├── src/
│   ├── main/
│   │   ├── java/com/finnode/auth/
│   │   │   │
│   │   │   ├── AuthServiceApplication.java          ← Punto de entrada del microservicio
│   │   │   │
│   │   │   ├── config/
│   │   │   │   ├── SecurityConfig.java              ← Configura Spring Security (rutas públicas, BCrypt)
│   │   │   │   ├── JwtConfig.java                   ← Parámetros del JWT: secret, expiración, issuer
│   │   │   │   └── KafkaProducerConfig.java         ← Configuración del producer de Kafka
│   │   │   │
│   │   │   ├── controller/
│   │   │   │   └── AuthController.java              ← Expone los 3 endpoints REST del servicio
│   │   │   │
│   │   │   ├── service/
│   │   │   │   ├── AuthService.java                 ← Lógica de negocio: register, login, refresh
│   │   │   │   └── JwtService.java                  ← Generación, firma y validación de tokens JWT
│   │   │   │
│   │   │   ├── repository/
│   │   │   │   └── UserRepository.java              ← Acceso a PostgreSQL: buscar por email, username
│   │   │   │
│   │   │   ├── model/
│   │   │   │   └── User.java                        ← Entidad JPA: id, email, password (hash), role, createdAt
│   │   │   │
│   │   │   ├── dto/
│   │   │   │   ├── RegisterRequest.java             ← Entrada: nombre, email, password (con @Valid)
│   │   │   │   ├── LoginRequest.java                ← Entrada: email, password
│   │   │   │   ├── RefreshRequest.java              ← Entrada: refresh_token
│   │   │   │   └── AuthResponse.java                ← Salida: access_token, refresh_token, expiry
│   │   │   │
│   │   │   ├── event/
│   │   │   │   └── UserRegisteredEvent.java         ← Payload del evento Kafka (userId, email, nombre)
│   │   │   │
│   │   │   └── exception/
│   │   │       ├── GlobalExceptionHandler.java      ← @ControllerAdvice: manejo uniforme de errores
│   │   │       ├── UserAlreadyExistsException.java  ← Lanzada si el email ya está registrado
│   │   │       └── InvalidCredentialsException.java ← Lanzada si email o password son incorrectos
│   │   │
│   │   └── resources/
│   │       └── application.yml                      ← Config: puerto, DB, Kafka, JWT secret/expiry
│   │
│   └── test/
│       └── java/com/finnode/auth/
│           ├── service/
│           │   ├── AuthServiceTest.java             ← Unit tests: register, login, refresh
│           │   └── JwtServiceTest.java              ← Unit tests: generación y validación de tokens
│           └── controller/
│               └── AuthControllerTest.java          ← Integration tests con MockMvc
│
├── pom.xml                                          ← Dependencias Maven del microservicio
└── Dockerfile                                       ← Imagen Docker del servicio
```

---

## 📐 Descripción de Cada Capa

### `config/`
Clases de configuración pura. No contienen lógica de negocio.

| Clase | Qué hace |
|-------|----------|
| `SecurityConfig` | Define qué rutas son públicas (`/auth/**`), configura el `PasswordEncoder` (BCrypt) y deshabilita CSRF (API REST stateless) |
| `JwtConfig` | Bean que carga desde `application.yml` el secret, el tiempo de vida del `access_token` (ej: 15 min) y del `refresh_token` (ej: 7 días) |
| `KafkaProducerConfig` | Configura el `KafkaTemplate` con serialización JSON para publicar el `UserRegisteredEvent` |

---

### `controller/`
Capa de entrada HTTP. Solo recibe, delega y devuelve respuestas. No contiene lógica de negocio.

| Clase | Qué hace |
|-------|----------|
| `AuthController` | Recibe los 3 requests (`register`, `login`, `refresh`), valida con `@Valid` y delega a `AuthService` |

---

### `service/`
Corazón del microservicio. Aquí vive toda la lógica de negocio.

| Clase | Qué hace |
|-------|----------|
| `AuthService` | Orquesta el flujo completo: validar entrada → interactuar con el repositorio → llamar a `JwtService` → publicar evento Kafka |
| `JwtService` | Genera tokens con `jjwt`, firma con el secret, extrae claims (userId, role), valida firma y expiración |

---

### `repository/`
Acceso a datos. Solo habla con PostgreSQL a través de JPA.

| Clase | Qué hace |
|-------|----------|
| `UserRepository` | Extiende `JpaRepository<User, UUID>`. Métodos: `findByEmail()`, `existsByEmail()` |

---

### `model/`
Entidades JPA que se mapean a tablas en PostgreSQL.

| Clase | Campos principales |
|-------|--------------------|
| `User` | `id` (UUID), `fullName`, `email` (único), `passwordHash`, `role` (ENUM: USER/ADMIN), `createdAt`, `active` |

---

### `dto/`
Objetos de transferencia de datos. Separan la API pública de la entidad interna.

| Clase | Dirección | Campos |
|-------|-----------|--------|
| `RegisterRequest` | Entrada | `fullName`, `email`, `password` — todos con validaciones `@NotBlank`, `@Email` |
| `LoginRequest` | Entrada | `email`, `password` |
| `RefreshRequest` | Entrada | `refreshToken` |
| `AuthResponse` | Salida | `accessToken`, `refreshToken`, `tokenType` ("Bearer"), `expiresIn` |

---

### `event/`
Contratos de los mensajes publicados a Kafka.

| Clase | Qué hace |
|-------|----------|
| `UserRegisteredEvent` | POJO serializable a JSON con `userId` (UUID), `email`, `fullName`, `timestamp`. Es consumido por `account-service` para crear la cuenta bancaria |

---

### `exception/`
Manejo centralizado de errores para respuestas HTTP consistentes.

| Clase | Qué hace |
|-------|----------|
| `GlobalExceptionHandler` | `@RestControllerAdvice` que captura excepciones y las convierte en respuestas JSON con código HTTP apropiado |
| `UserAlreadyExistsException` | → HTTP 409 Conflict |
| `InvalidCredentialsException` | → HTTP 401 Unauthorized |

---

## 🗄️ Modelo de Base de Datos

**Tabla: `users`**

```
┌──────────────────┬─────────────────┬─────────────────────────────────────┐
│ Columna          │ Tipo            │ Descripción                          │
├──────────────────┼─────────────────┼─────────────────────────────────────┤
│ id               │ UUID (PK)       │ Identificador único del usuario      │
│ full_name        │ VARCHAR(150)    │ Nombre completo                      │
│ email            │ VARCHAR(255)    │ Email único — usado para login       │
│ password_hash    │ VARCHAR(255)    │ Contraseña encriptada con BCrypt     │
│ role             │ VARCHAR(20)     │ ENUM: USER | ADMIN                   │
│ active           │ BOOLEAN         │ Cuenta habilitada/deshabilitada      │
│ created_at       │ TIMESTAMP       │ Fecha de registro                    │
└──────────────────┴─────────────────┴─────────────────────────────────────┘
```

> ⚠️ `auth-service` tiene su **propia instancia de PostgreSQL** aislada. Ningún otro microservicio accede a esta base de datos directamente.

---

## 📨 Evento Kafka Publicado

**Topic:** `user.registered`

```json
{
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "email": "juan@finnode.com",
  "fullName": "Juan Pérez",
  "timestamp": "2025-01-15T10:30:00Z"
}
```

**Consumidor:** `account-service` — al recibir este evento, crea automáticamente una cuenta bancaria para el nuevo usuario.

---

## 📦 Dependencias

**`pom.xml` — Spring Initializr:**

| Dependencia | Uso |
|-------------|-----|
| `spring-boot-starter-web` | Servidor HTTP y endpoints REST |
| `spring-boot-starter-data-jpa` | ORM con Hibernate para PostgreSQL |
| `postgresql` | Driver JDBC de PostgreSQL |
| `spring-boot-starter-security` | Seguridad base (BCrypt, filtros) |
| `spring-boot-starter-oauth2-resource-server` | Validación de JWT como Resource Server |
| `spring-boot-starter-validation` | Validaciones `@Valid`, `@NotBlank`, `@Email` |
| `spring-kafka` | Publicar eventos a Kafka |
| `lombok` | Reducir boilerplate (getters, constructores, builders) |

**Dependencia manual:**

```xml
<!-- pom.xml -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.x</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.x</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.x</version>
    <scope>runtime</scope>
</dependency>
```

---

## ⚙️ Variables de Entorno

Definidas en `application.yml` e inyectables como variables de entorno en Docker:

```yaml
server:
  port: 8081

spring:
  datasource:
    url: jdbc:postgresql://auth-db:5432/auth_db
    username: ${DB_USER}
    password: ${DB_PASSWORD}

  jpa:
    hibernate:
      ddl-auto: validate           # En producción: nunca auto-crear tablas
    show-sql: false

  kafka:
    bootstrap-servers: ${KAFKA_BROKER}
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JacksonJsonSerializer

jwt:
  secret: ${JWT_SECRET}            # Mínimo 256 bits en producción
  access-token-expiry: 900000      # 15 minutos en ms
  refresh-token-expiry: 604800000  # 7 días en ms
  issuer: finnode-auth
```

| Variable | Descripción | Ejemplo |
|----------|-------------|---------|
| `DB_USER` | Usuario de PostgreSQL | `auth_user` |
| `DB_PASSWORD` | Contraseña de PostgreSQL | `s3cr3t` |
| `KAFKA_BROKER` | Host y puerto de Kafka | `kafka:9092` |
| `JWT_SECRET` | Clave secreta para firmar tokens | `base64-encoded-256bit-key` |

---

## 🔄 Flujo Interno: Registro de Usuario

```
POST /auth/register
        │
        ▼
AuthController.register()
        │  @Valid RegisterRequest
        ▼
AuthService.register()
        ├── Verifica que el email no exista → UserAlreadyExistsException (409)
        ├── Encripta password con BCrypt
        ├── Persiste User en PostgreSQL
        ├── Publica UserRegisteredEvent → Kafka [user.registered]
        └── Retorna AuthResponse con access_token + refresh_token
```

## 🔄 Flujo Interno: Login

```
POST /auth/login
        │
        ▼
AuthController.login()
        │
        ▼
AuthService.login()
        ├── Busca User por email → InvalidCredentialsException (401) si no existe
        ├── Verifica password con BCrypt → InvalidCredentialsException (401) si no coincide
        └── Llama a JwtService
                ├── Genera access_token (claims: userId, email, role — expiry: 15 min)
                ├── Genera refresh_token (claim: userId — expiry: 7 días)
                └── Retorna AuthResponse
```

---

*`auth-service` — Porque la identidad es la primera puerta de todo sistema financiero seguro.*
