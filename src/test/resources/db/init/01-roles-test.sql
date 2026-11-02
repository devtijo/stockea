-- Mismos roles que infra/postgres/init/01-roles.sh, con claves de prueba.
CREATE ROLE stockea_migrator LOGIN PASSWORD 'test' NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
CREATE ROLE stockea_app LOGIN PASSWORD 'test' NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
CREATE DATABASE stockea OWNER stockea_migrator;
REVOKE ALL ON DATABASE stockea FROM PUBLIC;
GRANT CONNECT ON DATABASE stockea TO stockea_app;
\connect stockea
ALTER SCHEMA public OWNER TO stockea_migrator;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
