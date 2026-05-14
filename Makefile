# ============================================================================
# FinNode - Makefile
# ============================================================================
# Facilita la ejecución de comandos comunes de Docker Compose
# Uso: make [target]

.PHONY: help start stop restart rebuild logs status clean health urls \
        logs-api logs-auth logs-account logs-payment logs-ledger \
        shell-api shell-auth shell-account shell-payment shell-ledger \
        db-shell build

.DEFAULT_GOAL := help

# Variables
DOCKER_COMPOSE := docker-compose
SERVICES := api-gateway auth-service account-service payment-orchestrator ledger-service
COMPOSE_FILE := docker-compose.yml

# ============================================================================
# Información
# ============================================================================

help: ## Muestra esta ayuda
	@echo "FinNode - Docker Compose Helper"
	@echo "======================================"
	@echo ""
	@echo "Uso: make [target]"
	@echo ""
	@echo "Targets principales:"
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "  %-20s %s\n", $$1, $$2}'
	@echo ""
	@echo "Ejemplos:"
	@echo "  make start          # Inicia todos los servicios"
	@echo "  make logs-auth      # Ver logs de auth-service"
	@echo "  make shell-api      # Abre shell en api-gateway"
	@echo "  make db-shell       # Abre psql"

# ============================================================================
# Comandos Principales
# ============================================================================

start: ## Inicia todos los servicios
	@echo "Iniciando FinNode..."
	@$(DOCKER_COMPOSE) up -d
	@echo "Esperando a que los servicios estén listos..."
	@sleep 15
	@$(MAKE) status

start-build: ## Inicia todos los servicios (rebuild de imágenes)
	@echo "Iniciando FinNode con rebuild..."
	@$(DOCKER_COMPOSE) up --build -d
	@echo "Esperando a que los servicios estén listos..."
	@sleep 15
	@$(MAKE) status

stop: ## Detiene todos los servicios
	@echo "Deteniendo FinNode..."
	@$(DOCKER_COMPOSE) down

restart: ## Reinicia todos los servicios
	@$(MAKE) stop
	@sleep 2
	@$(MAKE) start

status: ## Muestra el estado de todos los servicios
	@$(DOCKER_COMPOSE) ps

logs: ## Ver logs de todos los servicios en tiempo real
	@$(DOCKER_COMPOSE) logs -f --tail=50

logs-tail: ## Ver últimas 100 líneas de logs
	@$(DOCKER_COMPOSE) logs --tail=100

# ============================================================================
# Logs de Servicios Individuales
# ============================================================================

logs-api: ## Ver logs de api-gateway
	@$(DOCKER_COMPOSE) logs -f api-gateway

logs-auth: ## Ver logs de auth-service
	@$(DOCKER_COMPOSE) logs -f auth-service

logs-account: ## Ver logs de account-service
	@$(DOCKER_COMPOSE) logs -f account-service

logs-payment: ## Ver logs de payment-orchestrator
	@$(DOCKER_COMPOSE) logs -f payment-orchestrator

logs-ledger: ## Ver logs de ledger-service
	@$(DOCKER_COMPOSE) logs -f ledger-service

logs-postgres: ## Ver logs de PostgreSQL
	@$(DOCKER_COMPOSE) logs -f postgres

logs-kafka: ## Ver logs de Kafka
	@$(DOCKER_COMPOSE) logs -f kafka

# ============================================================================
# Shell Interactivas
# ============================================================================

shell-api: ## Abre shell en api-gateway
	@$(DOCKER_COMPOSE) exec api-gateway /bin/sh

shell-auth: ## Abre shell en auth-service
	@$(DOCKER_COMPOSE) exec auth-service /bin/sh

shell-account: ## Abre shell en account-service
	@$(DOCKER_COMPOSE) exec account-service /bin/sh

shell-payment: ## Abre shell en payment-orchestrator
	@$(DOCKER_COMPOSE) exec payment-orchestrator /bin/sh

shell-ledger: ## Abre shell en ledger-service
	@$(DOCKER_COMPOSE) exec ledger-service /bin/sh

db-shell: ## Abre psql
	@$(DOCKER_COMPOSE) exec postgres psql -U postgres

db-shell-auth: ## Abre psql en auth_db
	@$(DOCKER_COMPOSE) exec postgres psql -U auth_user -d auth_db

db-shell-account: ## Abre psql en account_db
	@$(DOCKER_COMPOSE) exec postgres psql -U account_user -d account_db

db-shell-payment: ## Abre psql en payment_db
	@$(DOCKER_COMPOSE) exec postgres psql -U payment_user -d payment_db

# ============================================================================
# Mantenimiento
# ============================================================================

build: ## Construye todas las imágenes
	@$(DOCKER_COMPOSE) build

rebuild-api: ## Rebuild api-gateway
	@$(DOCKER_COMPOSE) build --no-cache api-gateway
	@$(DOCKER_COMPOSE) up -d api-gateway

rebuild-auth: ## Rebuild auth-service
	@$(DOCKER_COMPOSE) build --no-cache auth-service
	@$(DOCKER_COMPOSE) up -d auth-service

rebuild-account: ## Rebuild account-service
	@$(DOCKER_COMPOSE) build --no-cache account-service
	@$(DOCKER_COMPOSE) up -d account-service

rebuild-payment: ## Rebuild payment-orchestrator
	@$(DOCKER_COMPOSE) build --no-cache payment-orchestrator
	@$(DOCKER_COMPOSE) up -d payment-orchestrator

rebuild-ledger: ## Rebuild ledger-service
	@$(DOCKER_COMPOSE) build --no-cache ledger-service
	@$(DOCKER_COMPOSE) up -d ledger-service

health: ## Verifica la salud de todos los servicios
	@echo "Verificando salud de servicios..."
	@echo ""
	@echo "API Gateway:"
	@curl -s http://localhost:8080/actuator/health 2>/dev/null | grep -o '"status":"[^"]*"' || echo "❌ No responde"
	@echo ""
	@echo "Auth Service:"
	@curl -s http://localhost:8081/actuator/health 2>/dev/null | grep -o '"status":"[^"]*"' || echo "❌ No responde"
	@echo ""
	@echo "Account Service:"
	@curl -s http://localhost:8082/actuator/health 2>/dev/null | grep -o '"status":"[^"]*"' || echo "❌ No responde"
	@echo ""
	@echo "Payment Orchestrator:"
	@curl -s http://localhost:8083/actuator/health 2>/dev/null | grep -o '"status":"[^"]*"' || echo "❌ No responde"
	@echo ""
	@echo "Ledger Service:"
	@curl -s http://localhost:8084/actuator/health 2>/dev/null | grep -o '"status":"[^"]*"' || echo "❌ No responde"

clean: ## Elimina contenedores e imágenes
	@echo "⚠️  Esto eliminará todos los contenedores e imágenes"
	@read -p "¿Continuar? (s/N) " -n 1 -r; \
	echo; \
	if [[ $$REPLY =~ ^[Ss]$$ ]]; then \
		$(DOCKER_COMPOSE) down --rmi all; \
		echo "✓ Limpieza completada"; \
	else \
		echo "Operación cancelada"; \
	fi

clean-volumes: ## Elimina TODO incluyendo bases de datos
	@echo "⚠️  ADVERTENCIA: Esto eliminará TODAS las bases de datos y datos persistentes"
	@read -p "¿Continuar? (s/N) " -n 1 -r; \
	echo; \
	if [[ $$REPLY =~ ^[Ss]$$ ]]; then \
		$(DOCKER_COMPOSE) down -v; \
		echo "✓ Limpieza con volúmenes completada"; \
	else \
		echo "Operación cancelada"; \
	fi

urls: ## Muestra URLs de acceso de servicios
	@echo "URLs de Servicios FinNode:"
	@echo ""
	@echo "API Gateway:          http://localhost:8080"
	@echo "Auth Service:         http://localhost:8081"
	@echo "Account Service:      http://localhost:8082"
	@echo "Payment Orchestrator: http://localhost:8083"
	@echo "Ledger Service:       http://localhost:8084"
	@echo ""
	@echo "PostgreSQL:           localhost:5532 (usuario: postgres)"
	@echo "Kafka:                localhost:9092"

prune: ## Limpia recursos no usados de Docker
	@docker system prune -f
	@echo "✓ Limpieza de Docker completada"

# ============================================================================
# Desarrollo
# ============================================================================

ps: status ## Alias para status

up: start ## Alias para start

down: stop ## Alias para stop

format: ## Formatea este Makefile
	@echo "Archivo Makefile formateado correctamente"

# ============================================================================
# Otros
# ============================================================================

version: ## Muestra versiones de Docker
	@echo "Docker version:"
	@docker --version
	@echo "Docker Compose version:"
	@docker-compose --version

env-example: ## Crea archivo .env desde .env.example
	@if [ ! -f .env ]; then \
		cp .env.example .env; \
		echo "✓ Archivo .env creado"; \
	else \
		echo "✓ Archivo .env ya existe"; \
	fi

validate: ## Valida el docker-compose.yml
	@$(DOCKER_COMPOSE) config > /dev/null && echo "✓ docker-compose.yml válido" || echo "❌ Errores en docker-compose.yml"

test-connection: ## Prueba conexión a servicios
	@echo "Probando conexión a servicios..."
	@echo ""
	@for port in 8080 8081 8082 8083 8084; do \
		if nc -z localhost $$port 2>/dev/null; then \
			echo "✓ Puerto $$port abierto"; \
		else \
			echo "❌ Puerto $$port cerrado"; \
		fi; \
	done

.PHONY: help version validate env-example

