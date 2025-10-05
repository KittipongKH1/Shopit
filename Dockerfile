# ==============================
# Stage 1: Build jar with Maven
# ==============================
FROM maven:3.9.4-eclipse-temurin-17 AS build
WORKDIR /app

# Copy pom + source code
COPY pom.xml .
COPY src ./src

# Build jar (skip tests เพื่อให้เร็ว)
RUN mvn clean package -DskipTests

# ==============================
# Stage 2: Run the app
# ==============================
FROM openjdk:17-jdk-slim
WORKDIR /app

# Copy jar จาก stage ก่อนหน้า
COPY --from=build /app/target/Shopping_Cart.jar app.jar

# Expose port (Render จะ map port ของ service)
EXPOSE 8080

# Start Spring Boot
ENTRYPOINT ["java", "-jar", "app.jar"]

