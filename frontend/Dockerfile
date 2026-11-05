# syntax=docker/dockerfile:1
# --- Etapa 1: construir la PWA
FROM node:22-alpine AS construccion
WORKDIR /web
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npm run build

# --- Etapa 2: servir archivos estáticos con Nginx sin privilegios (puerto 8080).
# TLS, límites y cabeceras de seguridad los pone el gateway que está delante.
FROM nginxinc/nginx-unprivileged:1.29-alpine
USER root
RUN apk upgrade --no-cache
USER 101
COPY nginx-spa.conf /etc/nginx/conf.d/default.conf
COPY --from=construccion /web/dist /usr/share/nginx/html
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --retries=3 CMD wget -q -O /dev/null http://localhost:8080/ || exit 1
