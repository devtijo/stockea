#!/bin/bash
# Se ejecuta UNA vez, al crear el volumen de PostgreSQL (docker-entrypoint-initdb.d).
#   - stockea_migrator: dueño del esquema; solo lo usa Flyway.
#   - stockea_app:      la aplicación. Sin superusuario, sin BYPASSRLS, sin ser dueña:
#                       RLS le aplica y no puede alterar tablas ni deshabilitar triggers.
#   - keycloak:         base propia del proveedor de identidad.
set -euo pipefail

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
  CREATE ROLE stockea_migrator LOGIN PASSWORD '${STOCKEA_MIGRATOR_PASSWORD}'
    NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
  CREATE ROLE stockea_app LOGIN PASSWORD '${STOCKEA_APP_PASSWORD}'
    NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
  CREATE DATABASE stockea OWNER stockea_migrator;
  REVOKE ALL ON DATABASE stockea FROM PUBLIC;
  GRANT CONNECT ON DATABASE stockea TO stockea_app;

  CREATE ROLE keycloak LOGIN PASSWORD '${KEYCLOAK_DB_PASSWORD}';
  CREATE DATABASE keycloak OWNER keycloak;
EOSQL

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname stockea <<-EOSQL
  ALTER SCHEMA public OWNER TO stockea_migrator;
  REVOKE CREATE ON SCHEMA public FROM PUBLIC;
EOSQL
