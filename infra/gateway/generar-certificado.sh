#!/bin/sh
# Certificado TLS para el gateway en LOCAL. Solo lo crea si no existe, así que se
# respeta uno hecho con mkcert
set -eu
DIR=/certs
if [ -s "$DIR/stockea.crt" ] && [ -s "$DIR/stockea.key" ]; then
  echo "Certificado existente: no se toca."
else
  openssl req -x509 -newkey rsa:2048 -sha256 -days 365 -nodes \
    -keyout "$DIR/stockea.key" -out "$DIR/stockea.crt" \
    -subj "/CN=localhost/O=STOCKEA (desarrollo)" \
    -addext "subjectAltName=DNS:localhost,IP:127.0.0.1"
  echo "Certificado autofirmado creado (el navegador mostrará una advertencia la primera vez)."
fi

# El gateway (WAF) corre sin root como el usuario nginx (uid 101): se le entrega una copia que solo el puede leer
cp "$DIR/stockea.crt" "$DIR/stockea.key" /gateway-certs/
chown 101:101 /gateway-certs/stockea.crt /gateway-certs/stockea.key
chmod 644 /gateway-certs/stockea.crt
chmod 600 /gateway-certs/stockea.key
echo "Certificado entregado al gateway."
