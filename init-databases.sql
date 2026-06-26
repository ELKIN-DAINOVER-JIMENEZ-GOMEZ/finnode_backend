-- ============================================================================
-- Script de Inicialización de Bases de Datos para FinNode
-- ============================================================================

-- auth_db
CREATE DATABASE auth_db;
CREATE USER auth_user WITH ENCRYPTED PASSWORD 'Dainover';
GRANT ALL PRIVILEGES ON DATABASE auth_db TO auth_user;
\c auth_db
GRANT ALL ON SCHEMA public TO auth_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO auth_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO auth_user;

-- account_db
\c postgres
CREATE DATABASE account_db;
CREATE USER account_user WITH ENCRYPTED PASSWORD 'Dainover';
GRANT ALL PRIVILEGES ON DATABASE account_db TO account_user;
\c account_db
GRANT ALL ON SCHEMA public TO account_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO account_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO account_user;

-- payment_db
\c postgres
CREATE DATABASE payment_db;
CREATE USER payment_user WITH ENCRYPTED PASSWORD 'Dainover';
GRANT ALL PRIVILEGES ON DATABASE payment_db TO payment_user;
\c payment_db
GRANT ALL ON SCHEMA public TO payment_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO payment_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO payment_user;

-- ledger_db
\c postgres
CREATE DATABASE ledger_db;
CREATE USER ledger_user WITH ENCRYPTED PASSWORD 'Dainover';
GRANT ALL PRIVILEGES ON DATABASE ledger_db TO ledger_user;
\c ledger_db
GRANT ALL ON SCHEMA public TO ledger_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO ledger_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO ledger_user;