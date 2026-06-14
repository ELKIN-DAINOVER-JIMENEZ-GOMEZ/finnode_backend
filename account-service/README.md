
# 🏦 account-service — El Núcleo de las Cuentas Bancarias

> Microservicio de FinNode responsable del ciclo de vida completo de las cuentas bancarias.
Gestiona saldos, reserva de fondos y liberación de recursos con control de concurrencia. Es el servicio de mayor carga del sistema.

---

## 📋 Tabla de Contenidos

- [Responsabilidades](#-responsabilidades)
- [Endpoints](#-endpoints)
- [Estructura de Carpetas](#-estructura-de-carpetas)
- [Descripción de Cada Capa](#-descripción-de-cada-capa)
- [Modelo de Base de Datos](#-modelo-de-base-de-datos)
- [Eventos Kafka](#-eventos-kafka)
- [Control de Concurrencia](#-control-de-concurrencia)
- [Dependencias](#-dependencias)
- [Variables de Entorno](#-variables-de-entorno)
- [Docker Compose Local](#-docker-compose-local)

---

## ✅ Responsabilidades

| # | Responsabilidad | Descripción |
|---|----------------|-------------|
| 1 | **Crear cuenta** | Escucha el evento `UserRegisteredEvent` de Kafka y crea automáticamente una cuenta bancaria para el nuevo usuario |
| 2 | **Consultar saldo** | Expone el saldo actual de una cuenta (CQRS — Query, operación de solo lectura) |
| 3 | **Reservar fondos** | Bloquea el monto solicitado antes de ejecutar una transferencia para garantizar consistencia (CQRS — Command) |
| 4 | **Confirmar débito** | Descuenta definitivamente los fondos reservados al recibir confirmación del `payment-orchestrator` |
| 5 | **Acreditar fondos** | Suma el monto recibido al saldo del destinatario al completarse una transferencia exitosa |
| 6 | **Revertir reserva** | Libera los fondos previamente reservados si el pago falla (compensación del Patrón Saga) |

---

## 🌐 Endpoints

```
GET  /accounts/{accountId}/balance        → Consultar saldo de una cuenta
GET  /accounts/{accountId}                → Consultar detalle completo de una cuenta
POST /accounts/{accountId}/reserve        → Reservar fondos para una transferencia
POST /accounts/{accountId}/confirm-debit  → Confirmar débito definitivo
POST /accounts/{accountId}/credit         → Acreditar fondos al destinatario
POST /accounts/{accountId}/release        → Liberar reserva (rollback Saga)
```

> Todos los endpoints requieren JWT válido. El token es validado por el `api-gateway` antes de que la petición llegue a este servicio.

---

## 📁 Estructura de Carpetas

```
account-service/
├── src/
│   ├── main/
│   │   ├── java/com/finnode/account/
│   │   │   │
│   │   │   ├── AccountServiceApplication.java           ← Punto de entrada del microservicio
│   │   │   │
│   │   │   ├── config/
│   │   │   │   ├── SecurityConfig.java                  ← Configura Spring Security como Resource Server JWT
│   │   │   │   ├── KafkaConsumerConfig.java             ← Configuración del consumer de Kafka (UserRegisteredEvent)
│   │   │   │   └── KafkaProducerConfig.java             ← Configuración del producer de Kafka (eventos de cuenta)
│   │   │   │
│   │   │   ├── controller/
│   │   │   │   └── AccountController.java               ← Expone los 6 endpoints REST del servicio
│   │   │   │
│   │   │   ├── service/
│   │   │   │   └── AccountService.java                  ← Lógica de negocio: crear, reservar, confirmar, revertir
│   │   │   │
│   │   │   ├── repository/
│   │   │   │   └── AccountRepository.java               ← Acceso a PostgreSQL: buscar por userId, accountId
│   │   │   │
│   │   │   ├── model/
│   │   │   │   ├── Account.java                         ← Entidad JPA principal: saldo, estado, @Version
│   │   │   │   └── AccountStatus.java                   ← ENUM: ACTIVE | SUSPENDED | CLOSED
│   │   │   │
│   │   │   ├── dto/--
│   │   │   │   ├── AccountResponse.java                 ← Salida: accountId, userId, balance, status, createdAt
│   │   │   │   ├── BalanceResponse.java                 ← Salida simplificada: accountId, availableBalance
│   │   │   │   ├── ReserveFundsRequest.java             ← Entrada: amount, transactionId (con @Valid)
│   │   │   │   ├── ConfirmDebitRequest.java             ← Entrada: transactionId, amount
│   │   │   │   ├── CreditFundsRequest.java              ← Entrada: amount, transactionId, sourceAccountId
│   │   │   │   └── ReleaseReserveRequest.java           ← Entrada: transactionId, amount
│   │   │   │
│   │   │   ├── event/
│   │   │   │   ├── UserRegisteredEvent.java             ← Evento consumido desde Kafka [user.registered]
│   │   │   │   ├── FundsReservedEvent.java              ← Publicado cuando la reserva es exitosa
│   │   │   │   ├── FundsReservationFailedEvent.java     ← Publicado cuando no hay saldo suficiente
│   │   │   │   └── FundsReleasedEvent.java              ← Publicado cuando se revierte una reserva (Saga)
│   │   │   │
│   │   │   ├── kafka/
│   │   │   │   ├── AccountEventConsumer.java            ← Escucha [user.registered] y [payment.reverse]
│   │   │   │   └── AccountEventPublisher.java           ← Publica eventos al broker de Kafka
│   │   │   │
│   │   │   └── exception/
│   │   │       ├── GlobalExceptionHandler.java          ← @RestControllerAdvice: errores uniformes en JSON
│   │   │       ├── AccountNotFoundException.java        ← Lanzada si la cuenta no existe → HTTP 404
│   │   │       ├── InsufficientFundsException.java      ← Lanzada si el saldo es insuficiente → HTTP 422
│   │   │       └── AccountSuspendedException.java       ← Lanzada si la cuenta está inactiva → HTTP 403
│   │   │
│   │   └── resources/
│   │       └── application.yml                          ← Config: puerto, DB, Kafka, JWT issuer
│   │
│   └── test/
│       └── java/com/finnode/account/
│           ├── service/
│           │   └── AccountServiceTest.java              ← Unit tests: reserva, crédito, rollback, concurrencia
│           ├── kafka/
│           │   └── AccountEventConsumerTest.java        ← Tests del consumer: creación automática de cuenta
│           └── controller/
│               └── AccountControllerTest.java           ← Integration tests con MockMvc
│
├── pom.xml                                              ← Dependencias Maven del microservicio
└── Dockerfile                                           ← Imagen Docker del servicio
```

---

## 📐 Descripción de Cada Capa

### `config/`
Clases de configuración pura. No contienen lógica de negocio.

| Clase | Qué hace |
|-------|----------|
| `SecurityConfig` | Configura este servicio como Resource Server. Valida JWT usando la clave pública del `auth-service`. Todas las rutas requieren autenticación |
| `KafkaConsumerConfig` | Configura el consumer group `account-service-group`, deserialización JSON y política de manejo de errores (DLQ para mensajes fallidos) |
| `KafkaProducerConfig` | Configura el `KafkaTemplate` con serialización JSON para publicar `FundsReservedEvent` y `FundsReservationFailedEvent` |

---

### `controller/`
Capa de entrada HTTP. Solo recibe, valida y delega. No contiene lógica de negocio.

| Clase | Qué hace |
|-------|----------|
| `AccountController` | Expone los 6 endpoints REST. Extrae el `userId` del JWT para validar que el usuario solo acceda a sus propias cuentas. Delega toda la lógica a `AccountService` |

---

### `service/`
Corazón del microservicio. Toda la lógica de negocio reside aquí.

| Clase | Qué hace |
|-------|----------|
| `AccountService` | Orquesta todas las operaciones: crear cuenta desde evento Kafka, consultar saldo, reservar fondos con validación de disponibilidad, confirmar débito, acreditar y revertir. Usa `@Transactional` en todas las operaciones de escritura |

---

### `repository/`
Acceso a datos. Solo habla con PostgreSQL a través de JPA.

| Clase | Qué hace |
|-------|----------|
| `AccountRepository` | Extiende `JpaRepository<Account, UUID>`. Métodos: `findByUserId()`, `findByAccountNumber()`, `existsByUserId()` |

---

### `model/`
Entidades JPA que se mapean a tablas en PostgreSQL.

| Clase | Campos principales |
|-------|--------------------|
| `Account` | `id` (UUID), `userId` (UUID — FK lógica hacia auth-service), `accountNumber` (único, generado), `balance` (BigDecimal), `reservedBalance` (BigDecimal), `status` (ENUM), `version` (@Version — Optimistic Locking), `createdAt`, `updatedAt` |
| `AccountStatus` | ENUM con valores: `ACTIVE`, `SUSPENDED`, `CLOSED` |

> ⚠️ Se usan dos campos de saldo: `balance` (disponible real) y `reservedBalance` (monto bloqueado en tránsito). El saldo disponible para el usuario es `balance - reservedBalance`.

---

### `dto/`
Objetos de transferencia de datos. Separan la API pública de la entidad interna.

| Clase | Dirección | Campos |
|-------|-----------|--------|
| `AccountResponse` | Salida | `accountId`, `userId`, `accountNumber`, `balance`, `reservedBalance`, `availableBalance`, `status`, `createdAt` |
| `BalanceResponse` | Salida | `accountId`, `availableBalance`, `reservedBalance`, `currency` |
| `ReserveFundsRequest` | Entrada | `amount` (@Positive), `transactionId` (@NotBlank) |
| `ConfirmDebitRequest` | Entrada | `transactionId`, `amount` |
| `CreditFundsRequest` | Entrada | `amount`, `transactionId`, `sourceAccountId` |
| `ReleaseReserveRequest` | Entrada | `transactionId`, `amount` |

---

### `event/`
Contratos de los mensajes consumidos y publicados en Kafka.

| Clase | Dirección | Qué contiene |
|-------|-----------|--------------|
| `UserRegisteredEvent` | **Consumido** de `[user.registered]` | `userId`, `email`, `fullName`, `timestamp` — dispara la creación de la cuenta bancaria |
| `FundsReservedEvent` | **Publicado** a `[account.funds-reserved]` | `transactionId`, `accountId`, `amount`, `timestamp` — señal verde para el `payment-orchestrator` |
| `FundsReservationFailedEvent` | **Publicado** a `[account.funds-reservation-failed]` | `transactionId`, `accountId`, `reason`, `timestamp` — inicia compensación Saga |
| `FundsReleasedEvent` | **Publicado** a `[account.funds-released]` | `transactionId`, `accountId`, `amount`, `timestamp` — confirma reversión exitosa |

---

### `kafka/`
Capa de mensajería. Desacopla la lógica de negocio del transporte de eventos.

| Clase | Qué hace |
|-------|----------|
| `AccountEventConsumer` | `@KafkaListener` que escucha `user.registered` para crear cuentas nuevas, y `payment.reverse` para ejecutar rollbacks del Patrón Saga |
| `AccountEventPublisher` | Encapsula el `KafkaTemplate`. Métodos: `publishFundsReserved()`, `publishReservationFailed()`, `publishFundsReleased()` |

---

### `exception/`
Manejo centralizado de errores para respuestas HTTP consistentes.

| Clase | Qué hace |
|-------|----------|
| `GlobalExceptionHandler` | `@RestControllerAdvice` que captura excepciones y las convierte en respuestas JSON estandarizadas con código HTTP apropiado |
| `AccountNotFoundException` | → HTTP 404 Not Found |
| `InsufficientFundsException` | → HTTP 422 Unprocessable Entity |
| `AccountSuspendedException` | → HTTP 403 Forbidden |

---

## 🗄️ Modelo de Base de Datos

**Tabla: `accounts`**

```
┌──────────────────────┬──────────────────┬──────────────────────────────────────────────────────┐
│ Columna              │ Tipo             │ Descripción                                           │
├──────────────────────┼──────────────────┼──────────────────────────────────────────────────────┤
│ id                   │ UUID (PK)        │ Identificador único de la cuenta bancaria             │
│ user_id              │ UUID             │ Referencia lógica al usuario en auth-service          │
│ account_number       │ VARCHAR(20)      │ Número de cuenta único, generado al crear la cuenta   │
│ balance              │ DECIMAL(19,4)    │ Saldo total actual de la cuenta                       │
│ reserved_balance     │ DECIMAL(19,4)    │ Monto bloqueado en transferencias en tránsito         │
│ status               │ VARCHAR(20)      │ ENUM: ACTIVE | SUSPENDED | CLOSED                    │
│ version              │ BIGINT           │ Control de Optimistic Locking con @Version de JPA     │
│ created_at           │ TIMESTAMP        │ Fecha de creación de la cuenta                        │
│ updated_at           │ TIMESTAMP        │ Última modificación (actualizado automáticamente)     │
└──────────────────────┴──────────────────┴──────────────────────────────────────────────────────┘
```

> ⚠️ `account-service` tiene su **propia instancia de PostgreSQL** aislada. Ningún otro microservicio accede a esta base de datos directamente. La relación con `auth-service` es únicamente lógica mediante `userId`.

---

## 📨 Eventos Kafka

### Evento consumido

**Topic:** `user.registered`

```json
{
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "email": "juan@finnode.com",
  "fullName": "Juan Pérez",
  "timestamp": "2025-01-15T10:30:00Z"
}
```

**Acción:** Crea automáticamente una cuenta bancaria con saldo `0.0000` y estado `ACTIVE`.

---

### Eventos publicados

**Topic:** `account.funds-reserved`

```json
{
  "transactionId": "txn-9f1e2a3b-...",
  "accountId": "acc-4c5d6e7f-...",
  "amount": 500.00,
  "timestamp": "2025-01-15T10:31:05Z"
}
```

**Topic:** `account.funds-reservation-failed`

```json
{
  "transactionId": "txn-9f1e2a3b-...",
  "accountId": "acc-4c5d6e7f-...",
  "reason": "INSUFFICIENT_FUNDS",
  "timestamp": "2025-01-15T10:31:05Z"
}
```

**Consumidor de ambos:** `payment-orchestrator` — decide si continuar o revertir el Patrón Saga.

---

## 🔒 Control de Concurrencia

Este servicio implementa **Optimistic Locking** mediante la anotación `@Version` de Hibernate.

```java
@Entity
public class Account {
    // ...
    @Version
    private Long version;  // ← Hibernate incrementa este campo en cada UPDATE
}
```

**¿Por qué?** En un sistema de alta concurrencia, múltiples transferencias pueden intentar modificar el saldo de la misma cuenta al mismo tiempo. Sin control de concurrencia, dos hilos podrían leer el mismo saldo, modificarlo y sobreescribirse mutuamente, corrompiendo los datos.

**¿Cómo funciona?**

```
Hilo A lee cuenta: balance=1000, version=5
Hilo B lee cuenta: balance=1000, version=5

Hilo A ejecuta UPDATE → balance=800, version=6  ✅ éxito
Hilo B ejecuta UPDATE → WHERE version=5 → no encuentra fila ❌
    → Hibernate lanza OptimisticLockException
    → Spring reintenta la operación automáticamente
    → Hilo B relee: balance=800, version=6 → procesa correctamente
```

> Esto garantiza que el dinero nunca se duplique ni se pierda por condiciones de carrera, sin necesidad de bloqueos pesimistas que degradarían el rendimiento.

---

## 📦 Dependencias

**`pom.xml` — Spring Initializr:**

| Dependencia | Uso |
|-------------|-----|
| `spring-boot-starter-web` | Servidor HTTP y endpoints REST |
| `spring-boot-starter-data-jpa` | ORM con Hibernate, Optimistic Locking (`@Version`) |
| `postgresql` | Driver JDBC de PostgreSQL |
| `spring-boot-starter-security` | Seguridad base y filtros HTTP |
| `spring-boot-starter-oauth2-resource-server` | Validación de JWT como Resource Server |
| `spring-boot-starter-validation` | Validaciones `@Valid`, `@NotBlank`, `@Positive` |
| `spring-kafka` | Consumir y publicar eventos en Kafka |
| `lombok` | Reducir boilerplate (getters, constructores, builders) |

---

## ⚙️ Variables de Entorno

Definidas en `application.yml` e inyectables como variables de entorno en Docker:

```yaml
server:
  port: 8082

spring:
  datasource:
    url: jdbc:postgresql://account-db:5432/account_db
    username: ${DB_USER}
    password: ${DB_PASSWORD}

  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false

  kafka:
    bootstrap-servers: ${KAFKA_BROKER}
    consumer:
      group-id: account-service-group
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: "com.finnode.*"
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JacksonJsonSerializer

  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${JWT_ISSUER_URI}    # URI del auth-service para validar tokens
```

| Variable | Descripción | Ejemplo |
|----------|-------------|---------|
| `DB_USER` | Usuario de PostgreSQL | `account_user` |
| `DB_PASSWORD` | Contraseña de PostgreSQL | `s3cr3t` |
| `KAFKA_BROKER` | Host y puerto de Kafka | `kafka:9092` |
| `JWT_ISSUER_URI` | URI del emisor JWT para validación | `http://auth-service:8081` |

---

## 🐳 Docker Compose Local

Archivo: `account-service/docker-compose.yml`

Levanta `account-service`, `account-db` (PostgreSQL) y `kafka` (single-node KRaft).

```sh
cd /home/Dainover/Documentos/finnode/account-service
docker compose up -d --build
```

Ver logs del servicio:

```sh
cd /home/Dainover/Documentos/finnode/account-service
docker compose logs -f account-service
```

Detener y limpiar contenedores:

```sh
cd /home/Dainover/Documentos/finnode/account-service
docker compose down
```

Si el `auth-service` corre fuera de Docker, puedes ajustar el issuer antes de levantar:

```sh
cd /home/Dainover/Documentos/finnode/account-service
JWT_ISSUER_URI=http://host.docker.internal:8081 docker compose up -d --build
```

---

## 🔄 Flujo Interno: Creación de Cuenta (vía Kafka)

```
Kafka [user.registered]
        │
        ▼
AccountEventConsumer.onUserRegistered()
        │
        ▼
AccountService.createAccount()
        ├── Verifica que no exista ya una cuenta para ese userId
        ├── Genera número de cuenta único
        ├── Persiste Account con balance=0, status=ACTIVE
        └── Retorna (no publica evento — flujo reactivo al registro)
```

## 🔄 Flujo Interno: Reserva de Fondos (CQRS Command)

```
POST /accounts/{accountId}/reserve
        │
        ▼
AccountController.reserveFunds()
        │  @Valid ReserveFundsRequest
        ▼
AccountService.reserveFunds()
        ├── Busca cuenta → AccountNotFoundException (404) si no existe
        ├── Valida status=ACTIVE → AccountSuspendedException (403)
        ├── Valida (balance - reservedBalance) >= amount → InsufficientFundsException (422)
        ├── Incrementa reservedBalance con @Transactional + @Version (Optimistic Lock)
        └── Publica FundsReservedEvent → Kafka [account.funds-reserved]
                    ↓ si OptimisticLockException
        └── Publica FundsReservationFailedEvent → Kafka [account.funds-reservation-failed]
```

## 🔄 Flujo Interno: Reversión Saga (Compensación)

```
Kafka [payment.reverse]
        │
        ▼
AccountEventConsumer.onPaymentReverse()
        │
        ▼
AccountService.releaseFunds()
        ├── Busca cuenta por transactionId
        ├── Decrementa reservedBalance (devuelve fondos bloqueados)
        ├── Persiste con @Transactional
        └── Publica FundsReleasedEvent → Kafka [account.funds-released]
```

---

*`account-service` — Porque cada centavo debe saberse exactamente dónde está, en todo momento.*
