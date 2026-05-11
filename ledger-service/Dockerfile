# =============================================================================
# ETAPA 1: COMPILACIÓN (Builder Stage)
# =============================================================================
FROM maven:3.9.11-eclipse-temurin-21 AS builder

WORKDIR /workspace

# Copiamos primero los metadatos de Maven para aprovechar la cache de dependencias.
# Esto permite que las dependencias se descarguen una sola vez incluso si cambia
# el código fuente.
COPY .mvn .mvn
COPY mvnw pom.xml ./

# Hacemos ejecutable el script mvnw y descargamos las dependencias offline.
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

# Copiamos el código fuente y compilamos el JAR de Spring Boot.
# El flag -DskipTests evita ejecutar tests durante la compilación (más rápido).
COPY src ./src
RUN ./mvnw -B -DskipTests package

# =============================================================================
# ETAPA 2: RUNTIME (Imagen final optimizada)
# =============================================================================
FROM eclipse-temurin:21-jre-jammy AS runtime

WORKDIR /app

# Creamos un usuario no-root (spring) con UID 10001 para mayor seguridad.
# No ejecutar la aplicación como root reduce el riesgo de seguridad en caso
# de que el contenedor sea comprometido.
RUN useradd -r -u 10001 spring
USER spring

# Copiamos el JAR compilado desde la etapa builder a la imagen runtime.
COPY --from=builder /workspace/target/*.jar /app/app.jar

# Exponemos el puerto (ver: spring.application.name = ledger-service en application.yaml)
# El puerto se puede sobrescribir con variable de entorno SERVER_PORT.
EXPOSE 8084

# Comando de inicio: ejecuta la aplicación Spring Boot.
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

