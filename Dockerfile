# =========================
# 🏗️ Stage 1: Build the Spring Boot app
# =========================
FROM maven:3.9.9-eclipse-temurin-17 AS builder

# Set working directory inside container
WORKDIR /app

# Copy Maven build files first (for dependency caching)
COPY pom.xml .

# Download dependencies (will be cached unless pom.xml changes)
RUN mvn dependency:go-offline -B

# Copy the source code
COPY src src

# Build the application (skip tests for faster build)
RUN mvn clean package -DskipTests

# =========================
# 🚀 Stage 2: Create lightweight runtime image
# =========================
FROM eclipse-temurin:17-jre-focal

WORKDIR /app

# Copy the JAR from the builder stage
COPY --from=builder /app/target/*.jar app.jar

# Expose port (default 9090)
EXPOSE 9090

# Allow dynamic port override
ENV PORT=9090

# Run the application
ENTRYPOINT ["sh", "-c", "java -jar app.jar --server.port=${PORT}"]
