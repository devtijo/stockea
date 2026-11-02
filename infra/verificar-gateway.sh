#!/usr/bin/env bash
# Verifica los controles del API Gateway
set -u
URL="${1:-https://localhost}"
ok=0; falla=0
revisar() { # descripcion resultado(0/1)
  if [ "$2" -eq 0 ]; then echo "  ✅ $1"; ok=$((ok+1)); else echo "  ❌ $1"; falla=$((falla+1)); fi
}
estado() { curl -sk -o /dev/null -w '%{http_code}' "$@"; }
cabeceras() { curl -sk -D - -o /dev/null "$@" | tr -d '\r'; }

echo "TLS y enrutamiento"
[ "$(curl -s -o /dev/null -w '%{http_code}' http://localhost/)" = "301" ]; revisar "HTTP redirige a HTTPS (301)" $?
curl -sk -o /dev/null --tlsv1.1 --tls-max 1.1 "$URL/" 2>/dev/null; [ $? -ne 0 ]; revisar "TLS 1.1 rechazado (solo 1.2 y 1.3)" $?
[ "$(estado "$URL/")" = "200" ]; revisar "/ sirve la PWA" $?
[ "$(estado "$URL/api/catalogo")" = "401" ]; revisar "/api llega al backend y exige token (401)" $?
[ "$(estado "$URL/auth/realms/stockea/.well-known/openid-configuration")" = "200" ]; revisar "/auth llega a Keycloak" $?
[ "$(estado "$URL/health")" = "200" ]; revisar "/health público responde" $?

echo "Lo interno no se publica"
[ "$(estado "$URL/actuator/prometheus")" = "404" ]; revisar "/actuator bloqueado en el borde" $?
[ "$(estado "$URL/auth/admin/")" = "403" ]; revisar "Consola de Keycloak bloqueada en el borde" $?
[ "$(estado "$URL/auth/realms/master/")" = "403" ]; revisar "Realm master de Keycloak bloqueado" $?
[ "$(estado "$URL/.env")" = "404" ] && [ "$(estado "$URL/.git/config")" = "404" ]; revisar "Archivos ocultos (.env, .git) bloqueados" $?
curl -sk "$URL/health" | grep -q '"components"'; [ $? -ne 0 ]; revisar "/health no revela componentes internos" $?

echo "Cabeceras de seguridad"
H=$(cabeceras "$URL/")
echo "$H" | grep -qi '^strict-transport-security:'; revisar "HSTS" $?
echo "$H" | grep -qi "^content-security-policy:.*default-src 'self'"; revisar "Content-Security-Policy" $?
echo "$H" | grep -qi '^x-content-type-options: nosniff'; revisar "X-Content-Type-Options" $?
echo "$H" | grep -qi '^x-frame-options: SAMEORIGIN'; revisar "X-Frame-Options" $?
echo "$H" | grep -qi '^referrer-policy:'; revisar "Referrer-Policy" $?
echo "$H" | grep -qi '^permissions-policy:'; revisar "Permissions-Policy" $?
echo "$H" | grep -qi '^server: nginx$'; revisar "No revela la versión de Nginx" $?
echo "$H" | grep -qi '^x-request-id:'; revisar "X-Request-Id para correlación" $?

echo "Límites"
[ "$(estado -X TRACE "$URL/")" = "405" ]; revisar "Método TRACE rechazado (405)" $?
head -c 2000000 /dev/zero | tr '\0' 'a' > /tmp/cuerpo-grande.txt
[ "$(estado -X POST -H 'Content-Type: application/json' --data-binary @/tmp/cuerpo-grande.txt "$URL/api/sync/operaciones")" = "413" ]
revisar "Cuerpo de 2 MB rechazado (413, límite 1 MB)" $?
codigos=$(for i in $(seq 1 8); do estado -X POST -H 'Content-Type: application/json' -d '{}' "$URL/api/publico/negocios"; echo; done | sort | uniq -c | tr '\n' ' ')
echo "$codigos" | grep -q '429'; revisar "Registro de negocios limitado: 8 intentos seguidos -> 429 ($codigos)" $?

echo
echo "Resultado: $ok controles OK, $falla con falla."
[ "$falla" -eq 0 ]
