#!/bin/bash

# ============================================================================
# FinNode - QUICK START
# ============================================================================
# Este script muestra las instrucciones de inicio rápido

cat << 'EOF'

╔════════════════════════════════════════════════════════════════════════════╗
║                                                                            ║
║                    🚀 FINNODE - DOCKER COMPOSE SETUP 🚀                   ║
║                                                                            ║
║                           ✅ IMPLEMENTACIÓN COMPLETADA                    ║
║                                                                            ║
╚════════════════════════════════════════════════════════════════════════════╝

📦 ARCHIVOS CREADOS:
══════════════════════════════════════════════════════════════════════════════

✓ docker-compose.yml (7.3KB)
  → Configuración principal con todos los microservicios

✓ init-databases.sql (3.2KB)
  → Script para crear bases de datos y usuarios PostgreSQL

✓ .env.example
  → Plantilla de variables de entorno

✓ DOCKER_COMPOSE_README.md (6.3KB)
  → Documentación completa y detallada

✓ SETUP_COMPLETE.md (9.4KB)
  → Resumen técnico de la implementación

✓ finnode-docker.sh (11KB)
  → Script helper con comandos útiles

✓ Makefile (8.6KB)
  → Alternativa usando make para ejecutar comandos

✓ .gitignore
  → Configuración para control de versiones


🚀 EMPEZAR EN 3 PASOS:
══════════════════════════════════════════════════════════════════════════════

PASO 1: Crear archivo .env
────────────────────────────────────────────────────────────────────────────
$ cp .env.example .env

(Opcional: edita .env si necesitas cambiar OPENAI_API_KEY)


PASO 2: Iniciar todos los servicios
────────────────────────────────────────────────────────────────────────────
$ docker-compose up -d

o usando el script:
$ ./finnode-docker.sh start

o usando make:
$ make start


PASO 3: Verificar que todo funciona
────────────────────────────────────────────────────────────────────────────
$ docker-compose ps

o:
$ ./finnode-docker.sh health

o:
$ make health


✅ HECHO! Ahora accede a:
──────────────────────────────────────────────────────────────────────────────

    API Gateway:              http://localhost:8080
    Auth Service:             http://localhost:8081
    Account Service:          http://localhost:8082
    Payment Orchestrator:     http://localhost:8083
    Ledger Service:           http://localhost:8084


📋 SERVICIOS PRINCIPALES:
══════════════════════════════════════════════════════════════════════════════

  Service                  Port    Database   Kafka
  ──────────────────────────────────────────────────────
  API Gateway             8080    ✗          ✗
  Auth Service            8081    ✓          ✓
  Account Service         8082    ✓          ✓
  Payment Orchestrator    8083    ✓          ✓
  Ledger Service          8084    ✗          ✓

Infraestructura:
  PostgreSQL              5532
  Apache Kafka            9092


🔧 COMANDOS ÚTILES:
══════════════════════════════════════════════════════════════════════════════

Ver estado de servicios:
$ docker-compose ps
$ ./finnode-docker.sh status
$ make status

Ver logs en tiempo real:
$ docker-compose logs -f
$ ./finnode-docker.sh logs
$ make logs

Ver logs de un servicio específico:
$ docker-compose logs -f auth-service
$ ./finnode-docker.sh logs auth-service
$ make logs-auth

Entrar a shell de un servicio:
$ docker-compose exec auth-service /bin/sh
$ ./finnode-docker.sh shell auth-service
$ make shell-auth

Entrar a PostgreSQL:
$ docker-compose exec postgres psql -U postgres
$ ./finnode-docker.sh db-shell
$ make db-shell

Detener servicios:
$ docker-compose down
$ ./finnode-docker.sh stop
$ make stop

Reiniciar servicios:
$ docker-compose restart
$ ./finnode-docker.sh restart
$ make restart

Rebuild y reiniciar:
$ docker-compose up --build -d
$ ./finnode-docker.sh start-build
$ make start-build


🗄️ ACCESO A BASE DE DATOS:
══════════════════════════════════════════════════════════════════════════════

Host:       localhost
Port:       5532
Users:      postgres, auth_user, account_user, payment_user
Password:   postgres (para postgres), Dainover (para otros)

Ejemplo con pgAdmin, DBeaver, o cualquier cliente SQL:
  Servidor:  localhost:5532
  Usuario:   auth_user
  Contraseña: Dainover


❓ AYUDA Y DOCUMENTACIÓN:
══════════════════════════════════════════════════════════════════════════════

Documentación completa:
$ cat DOCKER_COMPOSE_README.md

Resumen técnico:
$ cat SETUP_COMPLETE.md

Ayuda del script:
$ ./finnode-docker.sh help

Ayuda de make:
$ make help


⚠️  IMPORTANTE - PARA PRODUCCIÓN:
══════════════════════════════════════════════════════════════════════════════

Esta configuración es SOLO PARA DESARROLLO.

Para producción, asegúrate de:
✗ Cambiar todas las contraseñas
✗ Generar JWT secrets seguros
✗ Usar variables de entorno para valores sensibles
✗ Implementar TLS/SSL
✗ Usar Docker secrets o Kubernetes
✗ Validar entradas de usuario
✗ Implementar rate limiting


🎯 PRÓXIMAS MEJORAS OPCIONALES:
══════════════════════════════════════════════════════════════════════════════

✓ Agregar Prometheus + Grafana (monitoreo)
✓ Agregar ELK Stack (logs centralizados)
✓ Agregar pgAdmin (UI para PostgreSQL)
✓ Agregar Kafka UI
✓ Agregar Redis (caching)
✓ Agregar Swagger UI (documentación API)


═══════════════════════════════════════════════════════════════════════════════

¿Preguntas? Consulta:
  - DOCKER_COMPOSE_README.md
  - SETUP_COMPLETE.md
  - Documentación oficial de cada servicio en su carpeta

¡Listo para desarrollar! 🚀

═══════════════════════════════════════════════════════════════════════════════

EOF

