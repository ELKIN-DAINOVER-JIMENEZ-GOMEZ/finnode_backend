-- ============================================================================
-- Script de Inicialización de Bases de Datos para FinNode
-- ============================================================================
-- Este script se ejecuta automáticamente cuando el contenedor de PostgreSQL
-- se inicia por primera vez. Crea todas las bases de datos y usuarios
-- necesarios para los diferentes microservicios.

-- ============================================================================
-- 1. Base de Datos para Auth Service
-- ============================================================================
CREATE DATABASE auth_db;
CREATE USER auth_user WITH ENCRYPTED PASSWORD 'Dainover';
ALTER ROLE auth_user SET client_encoding TO 'utf8';
ALTER ROLE auth_user SET default_transaction_isolation TO 'read committed';
ALTER ROLE auth_user SET default_transaction_deferrable TO on;
ALTER ROLE auth_user SET default_transaction_read_only TO off;
ALTER ROLE auth_user SET search_path TO public;
GRANT ALL PRIVILEGES ON DATABASE auth_db TO auth_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO auth_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO auth_user;

-- ============================================================================
-- 2. Base de Datos para Account Service
-- ============================================================================
CREATE DATABASE account_db;
CREATE USER account_user WITH ENCRYPTED PASSWORD 'Dainover';
ALTER ROLE account_user SET client_encoding TO 'utf8';
ALTER ROLE account_user SET default_transaction_isolation TO 'read committed';
ALTER ROLE account_user SET default_transaction_deferrable TO on;
ALTER ROLE account_user SET default_transaction_read_only TO off;
ALTER ROLE account_user SET search_path TO public;
GRANT ALL PRIVILEGES ON DATABASE account_db TO account_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO account_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO account_user;

-- ============================================================================
-- 3. Base de Datos para Payment Orchestrator
-- ============================================================================
CREATE DATABASE payment_db;
CREATE USER payment_user WITH ENCRYPTED PASSWORD 'Dainover';
ALTER ROLE payment_user SET client_encoding TO 'utf8';
ALTER ROLE payment_user SET default_transaction_isolation TO 'read committed';
ALTER ROLE payment_user SET default_transaction_deferrable TO on;
ALTER ROLE payment_user SET default_transaction_read_only TO off;
ALTER ROLE payment_user SET search_path TO public;
GRANT ALL PRIVILEGES ON DATABASE payment_db TO payment_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO payment_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO payment_user;

-- ============================================================================
-- Permisos Adicionales del usuario postgres (Super usuario)
-- ============================================================================
-- El usuario postgres ya existe con privilegios de superusuario
-- y puede hacer cualquier cosa en la base de datos.

