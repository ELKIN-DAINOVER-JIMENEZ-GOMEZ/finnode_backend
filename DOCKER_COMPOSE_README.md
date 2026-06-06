# FinNode - Docker Compose

Orquestación completa de microservicios FinNode usando Docker Compose.

## 📋 Estructura de Servicios

El proyecto contiene 5 microservicios principales orquestados por un API Gateway:

| Servicio | Puerto | Descripción | BD | Kafka |
|----------|--------|-------------|-------|-------|
| **API Gateway** | 8080 | Puerta de entrada a todos los servicios | - | - |
| **Auth Service** | 8081 | Autenticación y autorización | ✅ | ✅ |
| **Account Service** | 8082 | Gestión de cuentas de usuario | ✅ | ✅ |
| **Payment Orchestrator** | 8083 | Orquestación de pagos y detección de fraude | ✅ | ✅ |
| **Ledger Service** | 8084 | Registros de transacciones | - | ✅ |

### Infraestructura

- **PostgreSQL** (Puerto 5532): Base de datos compartida para 3 servicios
  - `auth_db` (usuario: `auth_user`)
  - `account_db` (usuario: `account_user`)
  - `payment_db` (usuario: `payment_user`)
- **Apache Kafka** (Puerto 9092): Message broker para comunicación asíncrona

## 🚀 Requisitos Previos

- Docker Engine 20.10+
- Docker Compose 2.0+
- Espacio en disco: ~3GB
- Procesador: Mínimo 4 cores recomendado
- RAM: Mínimo 8GB, se recomienda 12GB+

Verificar instalación:
```bash
docker --version
docker-compose --version
```

## ⚙️ Configuración Inicial

### 1. Clonar/Descargar el Proyecto

```bash
cd /home/Dainover/Documentos/finnode
```

### 2. Crear archivo `.env` (Opcional pero recomendado)

```bash
cp .env.example .env
```

Luego editar `.env` con valores reales, especialmente:
- `OPENAI_API_KEY`: Necesaria para la detección de fraude en Payment Orchestrator

## 🏃 Ejecución

### Iniciar todos los servicios

```bash
docker-compose up -d
```

El flag `-d` ejecuta en background. Para ver logs:
```bash
docker-compose logs -f
```

### Iniciar con rebuilds (si hay cambios de código)

```bash
docker-compose up --build -d
```

### Detener todos los servicios

```bash
docker-compose down
```

### Detener y eliminar volúmenes de datos

⚠️ Esto borra todas las bases de datos:
```bash
docker-compose down -v
```

## 🔍 Verificar Estado de Servicios

### Ver estado de contenedores

```bash
docker-compose ps
```

### Ver logs de un servicio específico

```bash
docker-compose logs -f auth-service
```

### Ver logs de los últimas 100 líneas

```bash
docker-compose logs --tail=100
```

### Verificar health checks

```bash
docker-compose ps --format "table {{.Service}}\t{{.Status}}"
```

## 📡 Acceder a los Servicios

Una vez iniciados, los servicios están disponibles en:

```
API Gateway:           http://localhost:8080
Auth Service:          http://localhost:8081
Account Service:       http://localhost:8082
Payment Orchestrator:  http://localhost:8083
Ledger Service:        http://localhost:8084
PostgreSQL:            localhost:5532
Kafka:                 localhost:9092
```

## 🗄️ Acceso a Base de Datos

Con un cliente PostgreSQL (pgAdmin, DBeaver, etc.):

```
Host: localhost
Port: 5532
Username: postgres / auth_user / account_user / payment_user
Password: postgres / Dainover / Dainover / Dainover
```

Ejemplo con psql (línea de comandos):
```bash
psql -h localhost -p 5532 -U auth_user -d auth_db
```

## 🔗 Flujos de Comunicación

```
Cliente HTTP
    ↓
API Gateway (8080)
    ↓
    ├─→ Auth Service (8081)
    ├─→ Account Service (8082)
    ├─→ Payment Orchestrator (8083)
    └─→ Ledger Service (8084)
    
    All Services
    ↓
    Kafka (9092) ← Comunicación asíncrona
    ↓
    PostgreSQL (5532) ← Persistencia de datos
```

## 🧪 Testing

### Verificar que API Gateway está funcionando

```bash
curl -s http://localhost:8080/actuator/health | jq .
```

### Verificar que Kafka está funcionando

```bash
docker-compose exec kafka kafka-broker-api-versions.sh --bootstrap-server localhost:9092
```

### Verificar conectividad de servicios

```bash
docker network inspect finnode_finnode-network
```

## 📝 Troubleshooting

### Puerto ya en uso

```bash
# Liberar puerto (ejemplo puerto 5532)
sudo lsof -i :5532
kill -9 <PID>
```

### Contenedor no inicia

```bash
# Ver logs detallados
docker-compose logs <service-name>

# Reiniciar un servicio específico
docker-compose restart <service-name>
```

### Error de conexión a BD

Asegurate que:
1. PostgreSQL está corriendo: `docker-compose ps postgres`
2. El servicio esperó a que la BD esté lista: revisar dependencias en docker-compose.yml

### Error de Kafka

Verificar que Kafka está iniciado:
```bash
docker-compose logs kafka
```

## 🔄 Reconstruir Servicios

Si hay cambios en el código Java:

```bash
# Opción 1: Rebuild y reinicia
docker-compose up --build -d <service-name>

# Opción 2: Reconstruir todas las imágenes
docker-compose build --no-cache
docker-compose up -d
```

## 🧹 Limpiar

### Eliminar contenedores detenidos

```bash
docker-compose down
```

### Eliminar imágenes construidas

```bash
docker-compose down --rmi all
```

### Eliminar volúmenes de datos (⚠️ Borrado permanente)

```bash
docker-compose down -v
```

## 📊 Monitoreo Avanzado

### Usar Docker Desktop Dashboard
- Abre Docker Desktop
- Pestaña "Containers" muestra estado en tiempo real

### Monitoreo con Portainer (opcional)

```bash
docker run -d -p 9000:9000 \
  -v /var/run/docker.sock:/var/run/docker.sock \
  portainer/portainer-ce
```

Acceder en: http://localhost:9000

## 🔐 Seguridad

⚠️ **IMPORTANTE**: Los valores en este docker-compose son para **DESARROLLO SOLAMENTE**.

Para producción:
1. Cambiar todas las contraseñas en `init-databases.sql`
2. Generar claves JWT seguras (mínimo 32 caracteres)
3. Usar variables de entorno en lugar de valores hardcodeados
4. Implementar TLS/SSL
5. Usar secrets de Docker o Kubernetes
6. Validar y securing la API Gateway

## 📚 Recursos Adicionales

- [Docker Compose Documentation](https://docs.docker.com/compose/)
- [PostgreSQL Docker Image](https://hub.docker.com/_/postgres)
- [Bitnami Kafka Docker Image](https://hub.docker.com/r/bitnami/kafka)
- [Spring Boot Docker Best Practices](https://spring.io/guides/topicals/spring-boot-docker/)

## 📞 Soporte

Para problemas específicos de cada servicio, revisar:
- `account-service/README.md`
- `auth-service/README.md`
- `api-gateway/README.md`
- `ledger-service/README.md`
- `payment-orchestrator/README.md`

