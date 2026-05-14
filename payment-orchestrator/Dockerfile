FROM maven:3.9.11-eclipse-temurin-21 AS builder
WORKDIR /workspace

# Copiamos primero metadatos de Maven para aprovechar cache de dependencias.
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

# Copiamos el codigo y empaquetamos el jar de Spring Boot.
COPY src ./src
RUN ./mvnw -B -DskipTests package

FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app

RUN useradd -r -u 10001 spring
USER spring

COPY --from=builder /workspace/target/*.jar /app/app.jar

EXPOSE 8083

ENTRYPOINT ["java", "-jar", "/app/app.jar"]

