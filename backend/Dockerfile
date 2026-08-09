# Multi-stage Dockerfile for InternTrack AI Spring Boot 3 / Java 17 Production Deployment
# Ensures completely self-contained cloud building and running on Render / Railway

FROM eclipse-temurin:25-jdk-jammy as builder
WORKDIR /app

# Copy Maven wrapper and configuration for layer caching
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Grant execute permissions on Maven wrapper script
RUN chmod +x ./mvnw
# Pre-fetch dependency layer
RUN ./mvnw dependency:go-offline -B || true

# Copy source code and assemble production JAR
COPY src src
RUN ./mvnw clean package -DskipTests -B

# Final runtime image (JRE only for minimal container footprint)
FROM eclipse-temurin:25-jre-jammy
WORKDIR /app

# Copy built JAR from builder container
COPY --from=builder /app/target/interntrack-ai-backend-1.0.0-SNAPSHOT.jar app.jar

# Expose primary cloud hosting ports
EXPOSE 8080
EXPOSE 10000

# Launch application container
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
