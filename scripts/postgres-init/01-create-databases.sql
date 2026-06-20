-- Database-per-service isolation for the local Postgres container.
-- Each domain service connects only to its own database with the shared dev role.
-- Run once by the postgres image's docker-entrypoint-initdb.d on first volume init.
CREATE DATABASE catalog_db;
CREATE DATABASE cart_db;
CREATE DATABASE order_db;
CREATE DATABASE payment_db;
