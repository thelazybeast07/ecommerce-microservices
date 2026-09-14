#!/usr/bin/env bash
# Runs ONCE, when the container starts with an empty data volume.
# To re-run it: docker compose down -v   (this deletes all local data)
#
# Creates one database and one login role per service. Each role owns only its
# own database, and PUBLIC is revoked, so user_service cannot even connect to
# ecommerce_order_db. This enforces "database per service" at the database level.
set -euo pipefail

create_service_database() {
  local database="$1" username="$2" password="$3"
  echo "Creating database '${database}' owned by role '${username}'"

  # psql variables (:"name" for identifiers, :'name' for literals) are quoted
  # safely by psql itself, so passwords with special characters are not a problem.
  psql -v ON_ERROR_STOP=1 \
       --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
       -v db="$database" -v usr="$username" -v pwd="$password" <<'EOSQL'
CREATE ROLE :"usr" WITH LOGIN PASSWORD :'pwd';
CREATE DATABASE :"db" OWNER :"usr";
REVOKE ALL ON DATABASE :"db" FROM PUBLIC;
EOSQL
}

create_service_database ecommerce_user_db    "$USER_DB_USERNAME"    "$USER_DB_PASSWORD"
create_service_database ecommerce_product_db "$PRODUCT_DB_USERNAME" "$PRODUCT_DB_PASSWORD"
create_service_database ecommerce_order_db   "$ORDER_DB_USERNAME"   "$ORDER_DB_PASSWORD"
