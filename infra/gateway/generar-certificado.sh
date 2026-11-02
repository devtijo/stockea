#!/bin/sh
# Certificado TLS para el gateway en LOCAL. Solo lo crea si no existe, así que se
# respeta uno hecho con mkcert
set -eu
DIR=/certs
if [ -s "$DIR/stockea.crt" ] && [ -s "$DIR/stockea.key" ]; then
  echo "Certificado existente: no se toca."
  exit 0
fi
openssl req -x509 -newkey rsa:2048 -sha256 -days 365 -nodes \
  -keyout "$DIR/stockea.key" -out "$DIR/stockea.crt" \
  -subj "/CN=localhost/O=STOCKEA (desarrollo)" \
  -addext "subjectAltName=DNS:localhost,IP:127.0.0.1"
chmod 644 "$DIR/stockea.crt" "$DIR/stockea.key"
echo "Certificado autofirmado creado (el navegador mostrará una advertencia la primera vez)."
