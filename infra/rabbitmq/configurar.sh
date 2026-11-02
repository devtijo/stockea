set -eu
# importa topología y crear usuarios de minimo privilegio

API="http://${RABBITMQ_HOST:-rabbitmq}:15672/api"
ADMIN="${RABBITMQ_ADMIN_USER}:${RABBITMQ_ADMIN_PASSWORD}"

llamar() { # metodo ruta json
  curl -fsS -u "$ADMIN" -X "$1" -H 'content-type: application/json' "$API$2" -d "$3" > /dev/null
}

echo "Esperando a RabbitMQ..."
until curl -fsS -u "$ADMIN" "$API/overview" > /dev/null 2>&1; do sleep 2; done

echo "Importando topología..."
curl -fsS -u "$ADMIN" -X POST -H 'content-type: application/json' \
  "$API/definitions" --data-binary @"${DEFINICIONES:-/config/definitions.json}" > /dev/null

llamar PUT /users/stockea_api    "{\"password\":\"${RABBITMQ_API_PASSWORD}\",\"tags\":\"\"}"
llamar PUT /users/stockea_worker "{\"password\":\"${RABBITMQ_WORKER_PASSWORD}\",\"tags\":\"\"}"

llamar PUT /permissions/stockea/stockea_api \
  '{"configure":"^$","write":"^stockea\\.ventas$","read":"^$"}'
llamar PUT /permissions/stockea/stockea_worker \
  '{"configure":"^$","write":"^$","read":"^analitica\\.velocidad-venta$"}'
# El administrador también entra al vhost para ver las colas en la consola.
llamar PUT "/permissions/stockea/${RABBITMQ_ADMIN_USER}" '{"configure":".*","write":".*","read":".*"}'

echo "RabbitMQ listo: topología importada y usuarios stockea_api / stockea_worker creados."
