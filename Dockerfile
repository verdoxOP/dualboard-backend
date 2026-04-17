<<<<<<< HEAD
# ---- Stage 1: Build ----
FROM eclipse-temurin:25-jdk-alpine AS build

WORKDIR /app

# Copy Maven wrapper and pom first for dependency caching
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Copy source and build
COPY src/ src/
RUN ./mvnw package -DskipTests -B

# ---- Stage 2: Runtime ----
FROM eclipse-temurin:25-jre-alpine

WORKDIR /app

# Create a non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY --from=build /app/target/*.jar app.jar

RUN chown appuser:appgroup app.jar
USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
=======
# syntax=docker/dockerfile:1

FROM maven:3.9.11-eclipse-temurin-25 AS build
WORKDIR /app

# Cache dependencies first for faster rebuilds.
COPY pom.xml ./
RUN mvn -B -ntp -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -B -ntp -DskipTests clean package

FROM eclipse-temurin:25-jre
WORKDIR /app

# Run as non-root user in the runtime image.
RUN groupadd --system spring && useradd --system --gid spring spring

COPY --from=build /app/target/*.jar /app/app.jar

EXPOSE 8080
USER spring
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

>>>>>>> 00ef879 (commit)
