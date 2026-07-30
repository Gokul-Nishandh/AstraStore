# Stage 1: Build
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Copy the entire root project so Gradle has access to all central files
COPY . .

# Accept the service name from docker-compose
ARG SERVICE
RUN chmod +x gradlew && ./gradlew :${SERVICE}:build -x test

# Stage 2: Runtime
FROM eclipse-temurin:21-jre
WORKDIR /app
ARG SERVICE

# Copy the specific service's compiled JAR file
COPY --from=build /app/${SERVICE}/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]