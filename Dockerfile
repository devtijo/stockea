# syntax=docker/dockerfile:1
# Imagen del backend STOCKEA. La MISMA imagen corre como API o como Worker
# según SPRING_PROFILES_ACTIVE (12-Factor V y VIII: build una vez, varios procesos).

# --- Etapa 1: compilar (no llega a la imagen final)
FROM maven:3.9-eclipse-temurin-21-alpine AS construccion
WORKDIR /fuente
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline
COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests package \
 && java -Djarmode=tools -jar target/*.jar extract --layers --launcher --destination extraido

# --- Etapa 2: ejecutar. Solo JRE (sin compilador ni Maven), usuario sin privilegios.
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S stockea && adduser -S -G stockea -H stockea
WORKDIR /app
# Capas de la menos a la más cambiante: un cambio de código no vuelve a bajar dependencias
COPY --from=construccion /fuente/extraido/dependencies/ ./
COPY --from=construccion /fuente/extraido/spring-boot-loader/ ./
COPY --from=construccion /fuente/extraido/snapshot-dependencies/ ./
COPY --from=construccion /fuente/extraido/application/ ./
USER stockea
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
# 8080 = API (la publica el gateway) · 8081 = gestión/métricas (solo red interna)
EXPOSE 8080 8081
HEALTHCHECK --interval=15s --timeout=3s --start-period=90s --retries=5 \
  CMD wget -q -O /dev/null http://localhost:8081/actuator/health/liveness || exit 1
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
