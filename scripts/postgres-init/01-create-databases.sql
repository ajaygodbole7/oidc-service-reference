-- Database-per-service isolation for the local Postgres container.
-- Each domain service connects only to its own database with the shared dev role.
-- Run once by the postgres image's docker-entrypoint-initdb.d on first volume init.
CREATE DATABASE catalog_db;
CREATE DATABASE cart_db;
CREATE DATABASE order_db;
CREATE DATABASE payment_db;
-- SpiceDB's own datastore. Relationships live here so they survive SpiceDB restarts
-- (the previous --datastore-engine=memory wiped every relationship on restart, forcing a reseed).
-- SpiceDB owns this database's schema via `spicedb datastore migrate head` (see the spicedb-migrate
-- service in compose.yaml); no domain service connects to it directly.
CREATE DATABASE spicedb_db;
