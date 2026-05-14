#!/bin/bash

# ============================================================================
# FinNode - Docker Compose Helper Script
# ============================================================================
# Script para facilitar la gestión de microservicios con Docker Compose
# Uso: ./finnode-docker.sh [comando]

set -e

# Colores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Directorio del script
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# =============================================================================
# Funciones de Utilidad
# =============================================================================

print_header() {
    echo -e "${BLUE}═══════════════════════════════════════════════════════${NC}"
    echo -e "${BLUE}  $1${NC}"
    echo -e "${BLUE}═══════════════════════════════════════════════════════${NC}"
}

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠ $1${NC}"
}

print_info() {
    echo -e "${BLUE}ℹ $1${NC}"
}

# =============================================================================
# Comandos Principales
# =============================================================================

start() {
    print_header "Iniciando FinNode - Todos los Servicios"

    if [ ! -f "${SCRIPT_DIR}/.env" ]; then
        print_warning ".env no encontrado, usando .env.example"
        if [ -f "${SCRIPT_DIR}/.env.example" ]; then
            cp "${SCRIPT_DIR}/.env.example" "${SCRIPT_DIR}/.env"
            print_info "Archivo .env creado. Por favor, edítalo con tus valores reales."
        fi
    fi

    cd "${SCRIPT_DIR}"
    docker-compose up -d

    print_success "Servicios iniciados!"
    print_info "Esperando a que los servicios estén listos..."
    sleep 15

    status
}

start_build() {
    print_header "Iniciando FinNode - Rebuildeando Imágenes"
    cd "${SCRIPT_DIR}"
    docker-compose up --build -d
    print_success "Servicios iniciados con nuevas imágenes!"
    sleep 15
    status
}

stop() {
    print_header "Deteniendo FinNode - Todos los Servicios"
    cd "${SCRIPT_DIR}"
    docker-compose down
    print_success "Servicios detenidos!"
}

restart() {
    print_header "Reiniciando FinNode - Todos los Servicios"
    stop
    sleep 2
    start
}

restart_service() {
    local service=$1
    if [ -z "$service" ]; then
        print_error "Especifica el nombre del servicio"
        list_services
        return 1
    fi

    print_header "Reiniciando $service"
    cd "${SCRIPT_DIR}"
    docker-compose restart "$service"
    print_success "Servicio $service reiniciado!"
}

clean() {
    print_header "Limpiando FinNode"
    print_warning "Esto eliminará contenedores e imágenes"
    read -p "¿Continuar? (s/n) " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Ss]$ ]]; then
        cd "${SCRIPT_DIR}"
        docker-compose down --rmi all
        print_success "Limpieza completada!"
    else
        print_info "Operación cancelada"
    fi
}

clean_volumes() {
    print_header "Limpiando FinNode - Incluyendo Volúmenes"
    print_warning "Esto eliminará TODAS las bases de datos y datos persistentes"
    read -p "¿Continuar? (s/n) " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Ss]$ ]]; then
        cd "${SCRIPT_DIR}"
        docker-compose down -v
        print_success "Limpieza con volúmenes completada!"
    else
        print_info "Operación cancelada"
    fi
}

status() {
    print_header "Estado de Servicios"
    cd "${SCRIPT_DIR}"
    docker-compose ps
}

logs() {
    local service=$1
    if [ -z "$service" ]; then
        print_header "Logs - Todos los Servicios"
        cd "${SCRIPT_DIR}"
        docker-compose logs -f --tail=50
    else
        print_header "Logs - $service"
        cd "${SCRIPT_DIR}"
        docker-compose logs -f "$service"
    fi
}

logs_tail() {
    local service=$1
    local lines=${2:-100}
    if [ -z "$service" ]; then
        cd "${SCRIPT_DIR}"
        docker-compose logs --tail="$lines"
    else
        cd "${SCRIPT_DIR}"
        docker-compose logs --tail="$lines" "$service"
    fi
}

health_check() {
    print_header "Verificando Salud de Servicios"

    local services=("api-gateway" "auth-service" "account-service" "payment-orchestrator" "ledger-service")
    local ports=("8080" "8081" "8082" "8083" "8084")

    for i in "${!services[@]}"; do
        local service="${services[$i]}"
        local port="${ports[$i]}"

        if curl -s "http://localhost:$port/actuator/health" > /dev/null 2>&1; then
            print_success "$service (puerto $port) - OK"
        else
            print_error "$service (puerto $port) - NO RESPONDE"
        fi
    done

    # Verificar Kafka
    if docker-compose exec -T kafka kafka-broker-api-versions.sh --bootstrap-server localhost:9092 > /dev/null 2>&1; then
        print_success "Kafka (puerto 9092) - OK"
    else
        print_error "Kafka (puerto 9092) - NO RESPONDE"
    fi

    # Verificar PostgreSQL
    if docker-compose exec -T postgres pg_isready -U postgres > /dev/null 2>&1; then
        print_success "PostgreSQL (puerto 5532) - OK"
    else
        print_error "PostgreSQL (puerto 5532) - NO RESPONDE"
    fi
}

shell() {
    local service=$1
    if [ -z "$service" ]; then
        print_error "Especifica el nombre del servicio"
        list_services
        return 1
    fi

    print_header "Shell Interactiva en $service"
    cd "${SCRIPT_DIR}"
    docker-compose exec "$service" /bin/sh
}

db_shell() {
    local db=${1:-postgres}
    local user=${2:-postgres}

    print_header "PostgreSQL Shell - Base de Datos: $db"
    cd "${SCRIPT_DIR}"
    docker-compose exec postgres psql -U "$user" -d "$db"
}

rebuild_service() {
    local service=$1
    if [ -z "$service" ]; then
        print_error "Especifica el nombre del servicio"
        list_services
        return 1
    fi

    print_header "Rebuildeando Servicio: $service"
    cd "${SCRIPT_DIR}"
    docker-compose build --no-cache "$service"
    docker-compose up -d "$service"
    print_success "Servicio $service rebuildeado y reiniciado!"
}

urls() {
    print_header "URLs de Servicios"
    echo -e "${GREEN}API Gateway:${NC}          http://localhost:8080"
    echo -e "${GREEN}Auth Service:${NC}         http://localhost:8081"
    echo -e "${GREEN}Account Service:${NC}      http://localhost:8082"
    echo -e "${GREEN}Payment Orchestrator:${NC}  http://localhost:8083"
    echo -e "${GREEN}Ledger Service:${NC}       http://localhost:8084"
    echo -e "${BLUE}─────────────────────────────────────────${NC}"
    echo -e "${YELLOW}PostgreSQL:${NC}           localhost:5532"
    echo -e "${YELLOW}Kafka:${NC}                localhost:9092"
}

list_services() {
    echo -e "${BLUE}Servicios disponibles:${NC}"
    echo "  - api-gateway"
    echo "  - auth-service"
    echo "  - account-service"
    echo "  - payment-orchestrator"
    echo "  - ledger-service"
    echo "  - postgres"
    echo "  - kafka"
}

show_help() {
    print_header "FinNode - Docker Compose Helper"
    echo -e "${YELLOW}Uso:${NC} $0 [comando] [opciones]"
    echo ""
    echo -e "${BLUE}Comandos Principales:${NC}"
    echo "  start              Inicia todos los servicios"
    echo "  start-build        Inicia todos los servicios (rebuild de imágenes)"
    echo "  stop               Detiene todos los servicios"
    echo "  restart            Reinicia todos los servicios"
    echo "  status             Muestra el estado de todos los servicios"
    echo ""
    echo -e "${BLUE}Logs y Debugging:${NC}"
    echo "  logs [servicio]    Ver logs en tiempo real (opcional: especifica servicio)"
    echo "  logs-tail [N]      Ver últimas N líneas (por defecto 100)"
    echo "  health             Verifica la salud de todos los servicios"
    echo "  shell [servicio]   Abre una shell interactiva en un servicio"
    echo "  db-shell [db]      Abre psql para una base de datos"
    echo ""
    echo -e "${BLUE}Mantenimiento:${NC}"
    echo "  restart [servicio] Reinicia un servicio específico"
    echo "  rebuild [servicio] Rebuild y reinicia un servicio"
    echo "  clean              Elimina contenedores e imágenes"
    echo "  clean-volumes      Elimina TODO incluyendo bases de datos"
    echo ""
    echo -e "${BLUE}Información:${NC}"
    echo "  urls               Muestra URLs de acceso de servicios"
    echo "  services           Lista servicios disponibles"
    echo "  help               Muestra esta ayuda"
    echo ""
    echo -e "${BLUE}Ejemplos:${NC}"
    echo "  $0 start"
    echo "  $0 logs auth-service"
    echo "  $0 restart account-service"
    echo "  $0 shell api-gateway"
    echo "  $0 db-shell auth_db auth_user"
}

# =============================================================================
# Main - Procesar Comando
# =============================================================================

main() {
    local cmd="${1:-help}"

    case "$cmd" in
        start)
            start
            ;;
        start-build|start-rebuild)
            start_build
            ;;
        stop)
            stop
            ;;
        restart)
            if [ -n "$2" ]; then
                restart_service "$2"
            else
                restart
            fi
            ;;
        status|ps)
            status
            ;;
        logs)
            logs "$2"
            ;;
        logs-tail|tail)
            logs_tail "$2" "$3"
            ;;
        health|health-check)
            health_check
            ;;
        shell)
            shell "$2"
            ;;
        db-shell|db)
            db_shell "$2" "$3"
            ;;
        rebuild)
            rebuild_service "$2"
            ;;
        clean)
            clean
            ;;
        clean-volumes)
            clean_volumes
            ;;
        urls)
            urls
            ;;
        services|list)
            list_services
            ;;
        help|--help|-h)
            show_help
            ;;
        *)
            print_error "Comando desconocido: $cmd"
            echo ""
            show_help
            exit 1
            ;;
    esac
}

# Ejecutar main con todos los argumentos
main "$@"

