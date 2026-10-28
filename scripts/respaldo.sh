#!/usr/bin/env bash
# Respaldo de la base de datos de api-sunat. Ahí están los XML firmados y las constancias (CDR) de la SUNAT, que hay que conservar.
#
# Uso:   DB_USUARIO=apisunat DB_CLAVE=... ./scripts/respaldo.sh
# Con docker compose:   docker compose exec -T mysql mysqldump -uroot -proot --single-transaction apisunat | gzip > respaldo.sql.gz
#
# Variables (todas opcionales menos DB_CLAVE):
#   DB_HOST (127.0.0.1)  DB_PUERTO (3306)  DB_NOMBRE (apisunat)  DB_USUARIO (apisunat)  DB_CLAVE
#   RESPALDO_DIR (./respaldos)   carpeta donde se guarda; se crea si no existe
# Este script NUNCA borra respaldos viejos: cuánto tiempo guardarlos lo decide quien lo use.
set -euo pipefail

: "${DB_CLAVE:?Falta DB_CLAVE (la clave de la base de datos)}"
DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PUERTO="${DB_PUERTO:-3306}"
DB_NOMBRE="${DB_NOMBRE:-apisunat}"
DB_USUARIO="${DB_USUARIO:-apisunat}"
RESPALDO_DIR="${RESPALDO_DIR:-./respaldos}"

mkdir -p "$RESPALDO_DIR"
archivo="$RESPALDO_DIR/${DB_NOMBRE}-$(date +%Y%m%d-%H%M%S).sql.gz"
parcial="$archivo.parcial"

# La clave va por el entorno y no por la línea de comandos, para que no se vea en la lista de procesos
export MYSQL_PWD="$DB_CLAVE"
# --protocol=tcp: con host "localhost" mysqldump usaría el socket local, que no existe si la base está en otro contenedor o máquina
mysqldump --protocol=tcp --host="$DB_HOST" --port="$DB_PUERTO" --user="$DB_USUARIO" \
  --single-transaction --no-tablespaces --routines --triggers "$DB_NOMBRE" | gzip > "$parcial"

# Un respaldo que no se puede leer no sirve: se comprueba antes de darlo por bueno
gzip -t "$parcial"
if [ ! -s "$parcial" ]; then
  echo "El respaldo quedó vacío" >&2
  rm -f "$parcial"
  exit 1
fi
mv "$parcial" "$archivo"
echo "$archivo"
