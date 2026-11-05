#!/usr/bin/env bash
# Análisis estático con SonarQube LOCAL (http://localhost:9000).
set -euo pipefail
cd "$(dirname "$0")/../.." || exit 1
: "${SONAR_TOKEN:?Defina SONAR_TOKEN con el token de SonarQube (no lo guarde en git)}"

# verify = pruebas (Testcontainers) + reporte de cobertura de JaCoCo; luego el análisis.
./mvnw -B verify sonar:sonar \
  -Dsonar.host.url="${SONAR_HOST_URL:-http://localhost:9000}" \
  -Dsonar.token="$SONAR_TOKEN" \
  -Dsonar.qualitygate.wait=true

echo "Resultados: ${SONAR_HOST_URL:-http://localhost:9000}/dashboard?id=stockea"
