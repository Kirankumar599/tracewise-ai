# Multi-stage Dockerfile for Spring Boot tracewise-api
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app

# Copy maven wrapper and pom.xml
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw

# Resolve dependencies
RUN ./mvnw dependency:go-offline -B || true

# Copy source and package jar
COPY src ./src
RUN ./mvnw clean package -DskipTests -B

# Runtime stage
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

ENV PORT=8081 \
    ML_SERVICE_URL=http://localhost:8000 \
    CORS_ALLOWED_ORIGINS=http://localhost:5173

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8081

HEALTHCHECK --interval=15s --timeout=5s --start-period=20s --retries=3 \
    CMD curl -f http://localhost:${PORT}/api/v1/health || exit 1

ENTRYPOINT ["sh", "-c", "java -Dserver.port=${PORT:-8081} -jar app.jar"]
