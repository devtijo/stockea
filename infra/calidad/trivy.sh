#!/usr/bin/env bash
# Escaneo de seguridad con Trivy
#   1) Imágenes propias
#   2) Repositorio
set -uo pipefail
cd "$(dirname "$0")/../.." || exit 1
SALIDA=target/trivy
mkdir -p "$SALIDA" "$HOME/.m2"
VERSION=0.76.1
trivy() {
  # TRIVY_DOCKER_ARGS: opciones extra de docker run (por ejemplo, un proxy corporativo)
  # shellcheck disable=SC2086
  docker run --rm ${TRIVY_DOCKER_ARGS:-} \
    -v /var/run/docker.sock:/var/run/docker.sock \
    -v trivy-cache:/root/.cache \
    -v "$HOME/.m2":/root/.m2:ro \
    -v "$PWD":/repo:ro \
    aquasec/trivy:$VERSION "$@"
}
falla=0
for imagen in stockea/backend:local stockea/frontend:local stockea/gateway:local; do
  archivo="$SALIDA/imagen-$(echo "$imagen" | cut -d/ -f2 | cut -d: -f1).txt"
  echo "== Imagen $imagen"
  trivy image --quiet --scanners vuln --severity HIGH,CRITICAL --ignore-unfixed --exit-code 1 \
    "$imagen" | tee "$archivo" || falla=1
done
echo "== Repositorio (dependencias, secretos, Dockerfiles)"
# Trivy necesita los pom de TODAS las dependencias (incluidos los BOM que importa Spring Boot). Si los
# pide uno por uno a Maven Central, este responde 429 (demasiadas peticiones) y el escaneo se cae.
# Por eso primero Maven los deja en ~/.m2 y Trivy los lee de ahí sin salir a internet (--offline-scan).
./mvnw -B -q dependency:go-offline || falla=1
# certs/ se salta: es el certificado LOCAL (ignorado por git), no algo que se publique
trivy fs --quiet --offline-scan --scanners vuln,secret,misconfig --severity HIGH,CRITICAL --ignore-unfixed --exit-code 1 \
  --skip-dirs /repo/node_modules --skip-dirs /repo/frontend/node_modules --skip-dirs /repo/target \
  --skip-dirs /repo/frontend/dist --skip-dirs /repo/infra/gateway/certs \
  /repo | tee "$SALIDA/repositorio.txt" || falla=1

echo
if [ "$falla" -eq 0 ]; then
  echo "Trivy: sin vulnerabilidades HIGH/CRITICAL con arreglo disponible."
else
  echo "Trivy: hay hallazgos o un paso falló (revise la salida de arriba y $SALIDA/)."
fi
exit "$falla"