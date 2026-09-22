-- Run in MySQL CLI: mysql -u root -p < database/create-schemas.sql
CREATE DATABASE IF NOT EXISTS dpdms_flood;
CREATE DATABASE IF NOT EXISTS dpdms_drought;
CREATE DATABASE IF NOT EXISTS dpdms_fire;
CREATE DATABASE IF NOT EXISTS dpdms_zoonotic;
CREATE DATABASE IF NOT EXISTS dpdms_mining;
CREATE DATABASE IF NOT EXISTS dpdms_auth;
-- Each service creates only its own tables from its schema.sql at startup.
