# Stage 1: Build application using Maven and Eclipse Temurin JDK 21
FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /app

# Cache dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source and build jar
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Minimal runtime image
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Create a non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Copy built artifact
COPY --from=build /app/target/*.jar app.jar

# Render assigns dynamic PORT environment variable (default fallback 8080)
ENV PORT=8080
EXPOSE ${PORT}

ENTRYPOINT ["sh", "-c", "java -Dserver.port=${PORT} -jar app.jar"]
