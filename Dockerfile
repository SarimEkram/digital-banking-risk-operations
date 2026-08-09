# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Cache dependencies first (leverages Docker layer caching)
COPY pom.xml .
RUN mvn -q dependency:go-offline

# Build the application
COPY src ./src
RUN mvn -q clean package -DskipTests

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Create a non-root user to run the app
RUN addgroup -S app && adduser -S app -G app

# Copy the built jar (as root) then hand ownership to the app user
COPY --from=build /app/target/*.jar app.jar
RUN chown app:app app.jar

USER app

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]